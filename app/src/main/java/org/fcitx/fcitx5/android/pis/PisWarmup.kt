/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import org.fcitx.fcitx5.android.pis.translation.LocalTranslationClient
import org.fcitx.fcitx5.android.pis.voice.PisVoiceRuntime
import org.fcitx.fcitx5.android.utils.InputMethodUtil

object PisWarmup {
    fun schedule(context: Context) {
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked || !InputMethodUtil.isSelected()) return
        val job = JobInfo.Builder(9010,ComponentName(context,PisWarmupJob::class.java))
            .setMinimumLatency(1000).setOverrideDeadline(30000).build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}
class PisWarmupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED)) PisWarmup.schedule(context)
    }
}
class PisWarmupJob : JobService() {
    private val main = Handler(Looper.getMainLooper())
    private var check: Runnable? = null
    override fun onStartJob(params: JobParameters): Boolean {
        if (!getSystemService(UserManager::class.java).isUserUnlocked || !InputMethodUtil.isSelected()) return false
        PisVoiceRuntime.prepare(this)
        LocalTranslationClient.prewarm(this)
        val began = SystemClock.elapsedRealtime()
        check = object : Runnable {
            override fun run() {
                if (LocalTranslationClient.prepared() && PisVoiceRuntime.engine(this@PisWarmupJob).ready) {
                    jobFinished(params,false); check = null
                } else if (SystemClock.elapsedRealtime()-began > 120000) {
                    jobFinished(params,false); check = null
                } else main.postDelayed(this,500)
            }
        }
        main.post(check!!)
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { check?.let(main::removeCallbacks); check = null; return false }
}
