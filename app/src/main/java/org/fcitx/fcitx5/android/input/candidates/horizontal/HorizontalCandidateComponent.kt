/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.horizontal

import android.content.res.Configuration
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.BooleanKey.ExpandedCandidatesEmpty
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.AlwaysFillWidth
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.AutoFillWidth
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.NeverFillWidth
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.theme
import org.mechdancer.dependency.manager.must
import timber.log.Timber

class HorizontalCandidateComponent :
    UniqueViewComponent<HorizontalCandidateComponent, RecyclerView>(), InputBroadcastReceiver {

    private val context by manager.context()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val inputView by manager.inputView()
    private val bar: KawaiiBarComponent by manager.must()

    private val fillStyle by AppPrefs.getInstance().keyboard.horizontalCandidateStyle
    private val maxSpanCountPref by lazy {
        AppPrefs.getInstance().keyboard.run {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                expandedCandidateGridSpanCount
            else
                expandedCandidateGridSpanCountLandscape
        }
    }

    private var layoutMinWidth = 0
    private var seedCandidates: Array<CandidateWord> = emptyArray()
    private var nativeCandidateData: FcitxEvent.CandidateListEvent.Data? = null
    private var candidateRevision = 0L
    private var pageJob: Job? = null
    private var reachedEnd = true
    private var compositionKey: Pair<String, String>? = null
    private var expandedOffset = 0
    private var expandedOffsetNeedsLayout = false

    private fun cancelPageLoad() {
        candidateRevision++
        pageJob?.cancel()
        pageJob = null
    }

    private fun updateMinimumWidth(width: Int) {
        val spans = maxSpanCountPref.getValue().coerceAtLeast(1)
        layoutMinWidth = when (fillStyle) {
            NeverFillWidth -> 0
            AutoFillWidth -> (width / spans).coerceAtLeast(0)
            AlwaysFillWidth -> (width / seedCandidates.size.coerceIn(1, spans)).coerceAtLeast(0)
        }
    }

    // Since expanded candidate window is created once the expand button was clicked,
    // we need to replay the last offset
    private val _expandedCandidateOffset = MutableSharedFlow<Int>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val expandedCandidateOffset = _expandedCandidateOffset.asSharedFlow()

    private fun refreshExpanded(childCount: Int) {
        expandedOffset = childCount
        _expandedCandidateOffset.tryEmit(childCount)
        bar.expandButtonStateMachine.push(
            ExpandedCandidatesUpdated,
            ExpandedCandidatesEmpty to (adapter.total >= 0 && adapter.total <= childCount)
        )
    }

    val adapter: HorizontalCandidateViewAdapter by lazy {
        object : HorizontalCandidateViewAdapter(theme) {
            override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                holder.itemView.minimumWidth = layoutMinWidth.coerceAtMost(holder.ui.maximumWidth)
                holder.itemView.setOnClickListener {
                    if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    inputView.service.emitPisFeedback(org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Pulse(
                        org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Kind.Confirm))
                    fcitx.launchOnReady { it.select(holder.idx) }
                }
                holder.itemView.setOnLongClickListener {
                    if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                    inputView.showCandidateMeaning(holder.idx, holder.candidate, holder.ui.root)
                    true
                }
            }

            override fun onViewRecycled(holder: CandidateViewHolder) {
                holder.itemView.setOnClickListener(null)
                holder.itemView.setOnLongClickListener(null)
                super.onViewRecycled(holder)
            }
        }
    }

    val layoutManager: LinearLayoutManager by lazy {
        object : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
            override fun canScrollVertically() = false
            override fun onLayoutCompleted(state: RecyclerView.State) {
                super.onLayoutCompleted(state)
                val firstRowCount = findLastVisibleItemPosition() + 1
                val atStart = findFirstVisibleItemPosition() == 0 &&
                    (findViewByPosition(0)?.left ?: -1) >= view.paddingLeft
                if (atStart && (expandedOffsetNeedsLayout || firstRowCount != expandedOffset)) {
                    // A newly loaded hint can widen the first row. Expand after its current end.
                    refreshExpanded(firstRowCount)
                    expandedOffsetNeedsLayout = false
                }
                // A very wide viewport may fit the initial 16 candidates completely.
                // Fill it enough to make later candidates reachable by touch/accessibility.
                view.post { loadMoreIfNeeded() }
            }
        }.apply { initialPrefetchItemCount = 2 }
    }

    override val view by lazy {
        object : RecyclerView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w != oldw) {
                    updateMinimumWidth(w)
                    // Update visible and cached holders only for a viewport resize.
                    adapter?.notifyItemRangeChanged(0, adapter?.itemCount ?: 0)
                    if (this@HorizontalCandidateComponent.layoutManager.findFirstVisibleItemPosition() <= 0) {
                        expandedOffsetNeedsLayout = true
                    }
                }
            }

            override fun onDetachedFromWindow() {
                cancelPageLoad()
                super.onDetachedFromWindow()
            }

            override fun onWindowVisibilityChanged(visibility: Int) {
                super.onWindowVisibilityChanged(visibility)
                if (visibility != View.VISIBLE) cancelPageLoad()
            }
        }.apply {
            id = R.id.candidate_view
            itemAnimator = null
            adapter = this@HorizontalCandidateComponent.adapter
            layoutManager = this@HorizontalCandidateComponent.layoutManager
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dx != 0) loadMoreIfNeeded()
                }
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) loadMoreIfNeeded()
                }
            })
        }
    }

    private fun loadMoreIfNeeded() {
        if (!view.isShown || view.isLayoutRequested || view.isComputingLayout || reachedEnd || pageJob?.isActive == true || adapter.itemCount == 0) return
        if (layoutManager.findLastVisibleItemPosition() < adapter.itemCount - PREFETCH_DISTANCE) return
        val nativeCandidates = nativeCandidateData ?: return
        if (fcitx.runImmediately { candidateListCached } !== nativeCandidates) return
        val revision = candidateRevision
        val offset = adapter.itemCount
        val nativeState = fcitx.runImmediately { clientPreeditCached to inputPanelCached }
        pageJob = inputView.service.lifecycleScope.launch {
            try {
                val page = fcitx.runOnReady { getCandidates(offset, PAGE_SIZE) }
                val sameNativeState = fcitx.runImmediately {
                    candidateListCached === nativeCandidates &&
                        clientPreeditCached === nativeState.first && inputPanelCached === nativeState.second
                }
                if (revision != candidateRevision || adapter.itemCount != offset || !sameNativeState) return@launch
                reachedEnd = page.size < PAGE_SIZE || (adapter.total >= 0 && offset + page.size >= adapter.total)
                adapter.appendCandidates(page)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Unable to load the next horizontal candidate page")
            } finally {
                if (revision == candidateRevision) pageJob = null
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        cancelPageLoad()
        seedCandidates = emptyArray()
        nativeCandidateData = null
        reachedEnd = true
        compositionKey = null
        expandedOffsetNeedsLayout = false
        adapter.updateCandidates(emptyArray(), 0)
        view.stopScroll()
        layoutManager.scrollToPosition(0)
        refreshExpanded(0)
    }

    override fun onCandidateUpdate(data: FcitxEvent.CandidateListEvent.Data) {
        val key = fcitx.runImmediately { clientPreeditCached.toString() to inputPanelCached.preedit.toString() }
        val changed = !seedCandidates.contentEquals(data.candidates) || adapter.total != data.total || compositionKey != key
        // A native update invalidates fetched tails, even if the first 16 words match.
        // Their later ordering is not guaranteed to be the same native list.
        cancelPageLoad()
        seedCandidates = data.candidates
        nativeCandidateData = data
        compositionKey = key
        reachedEnd = data.candidates.isEmpty() || (data.total >= 0 && data.candidates.size >= data.total)
        updateMinimumWidth(view.width)
        adapter.updateCandidates(data.candidates, data.total)
        if (data.candidates.isEmpty()) {
            expandedOffsetNeedsLayout = false
            refreshExpanded(0)
        } else if (changed) {
            view.stopScroll()
            expandedOffsetNeedsLayout = true
            layoutManager.scrollToPosition(0)
        } else {
            refreshExpanded(expandedOffset)
            view.post { loadMoreIfNeeded() }
        }
    }

    companion object {
        private const val PAGE_SIZE = 32
        private const val PREFETCH_DISTANCE = 4
    }
}
