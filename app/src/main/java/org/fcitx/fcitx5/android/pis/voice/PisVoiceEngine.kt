/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One serial native owner. No audio file, cloud call, or second recognition pass. */
class PisVoiceEngine(private val context: Context) {
    class Capture {
        val stopped = AtomicBoolean()
        val canceled = AtomicBoolean()
        val lock = Any()
        @Volatile var audio: AudioRecord? = null
        fun stop(cancel: Boolean) {
            if (cancel) canceled.set(true)
            synchronized(lock) { stopped.set(true); runCatching { audio?.stop() } }
        }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: OnlineRecognizer? = null
    private val preparing = AtomicBoolean()
    @Volatile var ready = false
        private set
    @Volatile private var closed = false
    fun prepare() {
        if (closed || ready || !preparing.compareAndSet(false,true)) return
        worker.execute { try { runCatching { ensureRecognizer() } } finally { preparing.set(false) } }
    }
    private fun ensureRecognizer(): OnlineRecognizer {
        return recognizer ?: OnlineRecognizer(context.assets, OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "voice/encoder-epoch-99-avg-1.int8.onnx",
                    decoder = "voice/decoder-epoch-99-avg-1.onnx",
                    joiner = "voice/joiner-epoch-99-avg-1.onnx"),
                tokens = "voice/tokens.txt", numThreads = 2, modelType = "zipformer", modelingUnit = "cjkchar"),
            enableEndpoint = false, decodingMethod = "modified_beam_search", maxActivePaths = 4, hotwordsScore = 1.5f
        )).also { recognizer = it; ready = true }
    }
    fun start(capture: Capture, snapshot: VoicePostProcessor.Snapshot, partial: (String) -> Unit,
              finished: (String) -> Unit, failed: (String) -> Unit,
              started: () -> Unit = {}, level: (Float) -> Unit = {}) {
        worker.execute {
            var stream: OnlineStream? = null
            var audio: AudioRecord? = null
            try {
                val model = ensureRecognizer()
                if (closed || capture.stopped.get()) {
                    if (!closed && !capture.canceled.get()) finished("")
                    return@execute
                }
                stream = VoicePipeline.createStream(model, context, snapshot)
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(minimum > 0) { "设备不支持当前录音格式" }
                audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 8192))
                require(audio.state == AudioRecord.STATE_INITIALIZED) { "无法打开麦克风" }
                synchronized(capture.lock) {
                    capture.audio = audio
                    if (capture.stopped.get() || closed) return@synchronized
                    audio.startRecording()
                }
                if (capture.stopped.get() || closed) {
                    if (!closed && !capture.canceled.get()) finished("")
                    return@execute
                }
                require(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风没有开始录音" }
                started()
                val began = SystemClock.elapsedRealtime()
                val shorts = ShortArray(1600)
                var previous = ""
                while (!capture.stopped.get() && !closed && SystemClock.elapsedRealtime() - began < 90000) {
                    val count = audio.read(shorts, 0, shorts.size)
                    if (count <= 0) { if (capture.stopped.get()) break; throw IllegalStateException("录音中断，请重试") }
                    val samples = FloatArray(count) { shorts[it] / 32768f }
                    var energy = 0.0
                    for (sample in samples) energy += sample * sample
                    level((kotlin.math.sqrt(energy / count).toFloat() * 8f).coerceIn(0f, 1f))
                    stream.acceptWaveform(samples, 16000)
                    while (model.isReady(stream)) model.decode(stream)
                    val text = model.getResult(stream).text.trim()
                    if (text != previous) { previous = text; partial(text) }
                }
                if (capture.canceled.get() || closed) return@execute
                stream.acceptWaveform(FloatArray(10560), 16000); stream.inputFinished()
                while (model.isReady(stream)) model.decode(stream)
                finished(model.getResult(stream).text.trim())
            } catch (error: Throwable) {
                if (!capture.canceled.get() && !closed) failed(error.message ?: "本地语音不可用，请重试")
            } finally {
                synchronized(capture.lock) { capture.audio = null; runCatching { audio?.stop() }; audio?.release() }
                stream?.release()
            }
        }
    }
    /** Real sample decoding for the independent native regression probe; microphone stays closed. */
    fun transcribe(samples: FloatArray, snapshot: VoicePostProcessor.Snapshot): String {
        return worker.submit<String> {
            val model = ensureRecognizer(); val stream = VoicePipeline.createStream(model, context, snapshot)
            try {
                var offset = 0
                while (offset < samples.size) {
                    val end = minOf(offset + 1600, samples.size)
                    stream.acceptWaveform(samples.copyOfRange(offset, end), 16000)
                    while (model.isReady(stream)) model.decode(stream)
                    offset = end
                }
                stream.acceptWaveform(FloatArray(10560), 16000); stream.inputFinished()
                while (model.isReady(stream)) model.decode(stream)
                model.getResult(stream).text.trim()
            } finally { stream.release() }
        }.get()
    }
    fun close() { closed = true; worker.execute { recognizer?.release(); recognizer = null; ready = false }; worker.shutdown() }
}
