package com.cxcboss.glassorb.voice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

interface LocalVoiceSessionListener {
    fun onRecordingStarted()
    fun onAudioLevel(level: Float)
    fun onAnalysisStarted()
    fun onResult(transcript: String)
    fun onError(message: String)
    fun onRecordingStopped()
}

/**
 * One-shot local microphone session. Capture begins immediately; model loading
 * happens on a separate worker and captured PCM is buffered until the model is
 * ready. No microphone is held between sessions.
 */
class LocalVoiceSession(
    private val context: Context,
    private val listener: LocalVoiceSessionListener,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GlassOrb-VoiceCapture")
    }
    private val modelExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GlassOrb-ASRModel")
    }
    private val active = AtomicBoolean(false)
    private val manualStop = AtomicBoolean(false)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var modelFuture: Future<OnlineRecognizer>? = null
    private val recognizerRef = AtomicReference<OnlineRecognizer?>(null)

    val isActive: Boolean get() = active.get()

    fun start() {
        if (!active.compareAndSet(false, true)) return
        manualStop.set(false)
        modelFuture = modelExecutor.submit<OnlineRecognizer> { createRecognizer() }
        captureExecutor.execute(::capture)
    }

    fun stopAndAnalyze() {
        if (active.get()) manualStop.set(true)
    }

    fun cancel() {
        active.set(false)
        manualStop.set(true)
        audioRecord?.let { recorder ->
            runCatching { recorder.stop() }
        }
    }

    fun release() {
        cancel()
        captureExecutor.shutdownNow()
        modelExecutor.shutdownNow()
        runCatching { captureExecutor.awaitTermination(2, TimeUnit.SECONDS) }
        runCatching { modelExecutor.awaitTermination(2, TimeUnit.SECONDS) }
        releaseRecognizer()
    }

    private fun capture() {
        var recorder: AudioRecord? = null
        var stream: OnlineStream? = null
        val bufferedChunks = ArrayDeque<FloatArray>()
        var bufferedSamples = 0
        var recognizer: OnlineRecognizer? = null
        var modelError: Throwable? = null
        var hasSpeech = false
        var analysisNotified = false
        var noiseFloor = 0.015f
        var smoothedLevel = 0f
        var silenceMs = 0L
        var baselineMs = 0L
        val sessionStart = SystemClock.elapsedRealtime()
        val shortBuffer = ShortArray(CHUNK_SAMPLES)

        try {
            recorder = createRecorder()
            audioRecord = recorder
            recorder.startRecording()
            post { listener.onRecordingStarted() }

            while (active.get() && SystemClock.elapsedRealtime() - sessionStart < MAX_SESSION_MS) {
                val read = recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                val samples = FloatArray(read)
                var sumSquares = 0.0
                for (index in 0 until read) {
                    val sample = shortBuffer[index] / 32768f
                    samples[index] = sample
                    sumSquares += sample * sample
                }
                val rms = sqrt(sumSquares / read).toFloat().coerceIn(0f, 1f)
                val chunkMs = read * 1_000L / SAMPLE_RATE
                baselineMs += chunkMs
                if (baselineMs <= BASELINE_MS) {
                    noiseFloor = noiseFloor * 0.85f + rms * 0.15f
                } else {
                    noiseFloor = noiseFloor * 0.995f + minOf(rms, noiseFloor * 1.3f) * 0.005f
                }
                val threshold = maxOf(0.018f, noiseFloor * 2.15f + 0.008f)
                val speaking = baselineMs > 220L && rms > threshold
                if (speaking) {
                    hasSpeech = true
                    silenceMs = 0L
                } else if (hasSpeech) {
                    silenceMs += chunkMs
                }
                val level = ((rms - noiseFloor) / 0.18f).coerceIn(0f, 1f)
                smoothedLevel = smoothedLevel * 0.72f + level * 0.28f
                post { listener.onAudioLevel(smoothedLevel) }

                if (stream == null) {
                    val future = modelFuture
                    if (future?.isDone == true) {
                        try {
                            recognizer = future.get()
                            recognizerRef.set(recognizer)
                            stream = recognizer.createStream()
                            bufferedChunks.forEach { chunk -> feed(recognizer, stream, chunk) }
                            bufferedChunks.clear()
                            bufferedSamples = 0
                        } catch (error: Throwable) {
                            modelError = error
                            break
                        }
                    } else if (bufferedSamples < SAMPLE_RATE * MAX_SESSION_SECONDS) {
                        bufferedChunks.addLast(samples)
                        bufferedSamples += samples.size
                    }
                }
                if (stream != null) feed(recognizer, stream, samples)

                if (!analysisNotified && (manualStop.get() || (hasSpeech && silenceMs >= SILENCE_TO_ANALYZE_MS))) {
                    analysisNotified = true
                    post { listener.onAnalysisStarted() }
                    break
                }
            }

            if (modelError != null) throw modelError
            if (!hasSpeech) {
                post { listener.onError("没有听清，请靠近麦克风再说一次") }
                return
            }
            if (!analysisNotified) {
                analysisNotified = true
                post { listener.onAnalysisStarted() }
            }
            if (stream == null) {
                val future = requireNotNull(modelFuture)
                recognizer = future.get()
                stream = recognizer.createStream()
                bufferedChunks.forEach { chunk -> feed(recognizer, stream, chunk) }
                bufferedChunks.clear()
            }
            val finalStream = requireNotNull(stream)
            finalStream.inputFinished()
            while (recognizer?.isReady(finalStream) == true) recognizer?.decode(finalStream)
            val text = recognizer?.getResult(finalStream)?.text?.trim().orEmpty()
            if (text.isBlank()) post { listener.onError("没有识别到清晰指令") }
            else post { listener.onResult(text) }
        } catch (error: Throwable) {
            if (active.get()) post { listener.onError("语音识别失败：${error.message ?: "本地模型不可用"}") }
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            audioRecord = null
            runCatching { stream?.release() }
            releaseRecognizer()
            active.set(false)
            post { listener.onRecordingStopped() }
        }
    }

    private fun feed(recognizer: OnlineRecognizer?, stream: OnlineStream?, samples: FloatArray) {
        val actualRecognizer = recognizer ?: return
        val actualStream = stream ?: return
        actualStream.acceptWaveform(samples, SAMPLE_RATE)
        while (actualRecognizer.isReady(actualStream)) actualRecognizer.decode(actualStream)
    }

    private fun createRecognizer(): OnlineRecognizer {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            "录音权限未授予"
        }
        val model = OnlineModelConfig(
            zipformer2Ctc = OnlineZipformer2CtcModelConfig("asr/model.int8.onnx"),
            tokens = "asr/tokens.txt",
            numThreads = 1,
            provider = "cpu",
            modelingUnit = "cjkchar",
        )
        val recognizer = OnlineRecognizer(
            context.assets,
            OnlineRecognizerConfig(
                featConfig = FeatureConfig(SAMPLE_RATE, 80, 0f),
                modelConfig = model,
                enableEndpoint = false,
                decodingMethod = "greedy_search",
            ),
        )
        recognizerRef.set(recognizer)
        if (!active.get()) {
            releaseRecognizer()
            throw CancellationException("语音会话已取消")
        }
        return recognizer
    }

    private fun releaseRecognizer() {
        runCatching { recognizerRef.getAndSet(null)?.release() }
    }

    private fun createRecorder(): AudioRecord {
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "设备不支持录音采集" }
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum * 2, CHUNK_SAMPLES * 2),
        ).also { check(it.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" } }
    }

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_024
        const val BASELINE_MS = 320L
        const val SILENCE_TO_ANALYZE_MS = 780L
        const val MAX_SESSION_SECONDS = 15
        const val MAX_SESSION_MS = MAX_SESSION_SECONDS * 1_000L
    }
}
