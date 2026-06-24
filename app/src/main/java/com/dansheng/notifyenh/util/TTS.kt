package com.dansheng.notifyenh.util

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dansheng.notifyenh.App
import java.io.File
import java.util.Locale

object TTS : TextToSpeech.OnInitListener, UtteranceProgressListener() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsIsInitRelease = false
    private val speakList = arrayListOf<String>()

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val audioFile by lazy { File(App.instance.cacheDir, "tts_output.wav") }

    private val ttsReleaseRunnable = Runnable {
        tts?.shutdown()
        tts = null
        ttsIsInitRelease = false
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaPlayer?.release()
        mediaPlayer = null
        if (audioFile.exists()) {
            audioFile.delete()
        }
    }

    @Synchronized
    fun speak(text: String) {
        mainHandler.removeCallbacks(ttsReleaseRunnable)
        speakList.add(text)
        if (tts == null) {
            tts = TextToSpeech(App.instance, this)
        } else if (ttsIsInitRelease) {
            processNext()
        }
    }

    @Synchronized
    private fun processNext() {
        if (!ttsIsInitRelease || speakList.isEmpty()) return

        // 如果正在播放，等待播放完成后的回调触发下一次 processNext
        if (mediaPlayer?.isPlaying == true) return

        val text = speakList.removeAt(0)
        val utteranceId = "tts_${System.currentTimeMillis()}"

        // 合成前删除旧文件，确保如果合成失败，不会播放出旧音频
        if (audioFile.exists()) {
            audioFile.delete()
        }

        // 使用 synthesizeToFile 生成音频文件
        val params = Bundle()
        tts?.synthesizeToFile(text, params, audioFile, utteranceId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(this)
            ttsIsInitRelease = true
            processNext()
        }
    }

    override fun onStart(utteranceId: String?) {
        // 开始生成/朗读取消释放资源任务
        mainHandler.removeCallbacks(ttsReleaseRunnable)
    }

    override fun onDone(utteranceId: String?) {
        // 合成完成，回到主线程播放文件
        mainHandler.post {
            playAudioFile()
        }
    }

    private fun playAudioFile() {
        if (!audioFile.exists()) {
            processNext()
            return
        }

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
        } else {
            mediaPlayer?.reset()
        }

        try {
            mediaPlayer?.apply {
                setDataSource(audioFile.absolutePath)
                prepare()

                // 设置音量增益：1.5倍线性增益约为 352 毫贝尔 (mB)
                // 20 * log10(1.5) * 100 = 352
                try {
                    loudnessEnhancer?.release()
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        setTargetGain(360)
                        enabled = true
                    }
                } catch (e: Exception) {
                    LogUtils.e("Error setting LoudnessEnhancer", e)
                }

                setOnCompletionListener {
                    // 播放完成后，尝试处理下一条
                    processNext()
                    // 如果队列空了，启动延迟释放任务
                    if (speakList.isEmpty()) {
                        mainHandler.postDelayed(ttsReleaseRunnable, 60000L)
                    }
                }
                start()
            }
        } catch (e: Exception) {
            LogUtils.e("Error playing TTS file", e)
            processNext()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) {
        processNext()
    }
}
