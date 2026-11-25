package com.example.echo_fx

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.max

class MainActivity : FlutterActivity() {

    private val channelName = "echo_fx/audio"

    @Volatile
    private var running = false

    @Volatile
    private var wet = 0.55f

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var worker: Thread? = null

    private val sampleRate = 44100
    private val delayMs = 320
    private val feedback = 0.28f

    override fun configureFlutterEngine(
        flutterEngine: FlutterEngine
    ) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            channelName
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                "start" -> {
                    wet = (
                        (call.argument<Int>("level") ?: 55)
                            .coerceIn(0, 100) / 100f
                    )

                    if (
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            701
                        )

                        result.error(
                            "MIC_PERMISSION",
                            "اسمح للتطبيق باستخدام المايك ثم اضغط تشغيل مرة ثانية",
                            null
                        )
                    } else {
                        try {
                            startAudio()
                            result.success(true)
                        } catch (e: Exception) {
                            stopAudio()

                            result.error(
                                "AUDIO_START",
                                e.message ?: "تعذر تشغيل الصوت",
                                null
                            )
                        }
                    }
                }

                "setLevel" -> {
                    wet = (
                        (call.argument<Int>("level") ?: 55)
                            .coerceIn(0, 100) / 100f
                    )

                    result.success(true)
                }

                "stop" -> {
                    stopAudio()
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    @Synchronized
    private fun startAudio() {
        if (running) return

        val minIn = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val minOut = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minIn <= 0 || minOut <= 0) {
            throw IllegalStateException(
                "الجهاز لا يدعم إعدادات الصوت المطلوبة"
            )
        }

        val bufferSize = max(minIn, minOut) * 2

        val formatIn = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val formatOut = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(formatIn)
            .setBufferSizeInBytes(bufferSize)
            .build()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(
                AudioAttributes.CONTENT_TYPE_SPEECH
            )
            .build()

        player = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(formatOut)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (
            recorder?.state != AudioRecord.STATE_INITIALIZED ||
            player?.state != AudioTrack.STATE_INITIALIZED
        ) {
            throw IllegalStateException(
                "فشل تهيئة نظام الصوت"
            )
        }

        running = true

        audioManager.mode =
            AudioManager.MODE_IN_COMMUNICATION

        audioManager.isSpeakerphoneOn = true

        recorder?.startRecording()
        player?.play()

        worker = Thread {
            val input = ShortArray(1024)

            val delaySamples =
                sampleRate * delayMs / 1000

            val delayLine =
                FloatArray(delaySamples.coerceAtLeast(1))

            var index = 0

            while (running) {

                val count = recorder?.read(
                    input,
                    0,
                    input.size
                ) ?: -1

                if (count <= 0) continue

                val output = ShortArray(count)

                val amount = wet

                for (i in 0 until count) {

                    val dry = input[i].toFloat()
                    val delayed = delayLine[index]

                    delayLine[index] = (
                        dry + delayed * feedback
                    ).coerceIn(-30000f, 30000f)

                    index = (index + 1) % delayLine.size

                    val mixed = (
                        dry * (1f - amount * 0.65f) +
                        delayed * amount * 1.25f
                    ).coerceIn(-32768f, 32767f)

                    output[i] = mixed.toInt().toShort()
                }

                player?.write(
                    output,
                    0,
                    count
                )
            }
        }.apply {
            name = "EchoFxAudio"
            start()
        }
    }

    @Synchronized
    private fun stopAudio() {
        running = false

        try {
            worker?.join(400)
        } catch (_: InterruptedException) {
        }

        worker = null

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            player?.stop()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null

        try {
            player?.release()
        } catch (_: Exception) {
        }

        player = null

        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private val audioManager: AudioManager
        get() = getSystemService(AUDIO_SERVICE) as AudioManager

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}