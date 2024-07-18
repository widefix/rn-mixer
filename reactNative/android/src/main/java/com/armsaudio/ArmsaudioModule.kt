package com.armsaudio

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.ReadableArray
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch

data class AudioTrack(
    val fileName: String,
    val player: ExoPlayer,
    var volume: Float = 1.0f,
    var pan: Float = 0.0f
)

class ArmsaudioModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "Armsaudio"
    }

    override fun getName(): String {
        return NAME
    }

    private var isMixPaused = false
    private var pausedTime = 0
    private var playerDeviceCurrTime = 0L
    private var audioTracks = mutableListOf<AudioTrack>()
    private var downloadProgress = 0.0
    private var maxPlaybackDuration = 0
    private val audioManager: AudioManager by lazy {
        reactApplicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var amplitudeTimers = mutableMapOf<String, Job>()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> handleAudioFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> handleAudioFocusLossTransient()
            AudioManager.AUDIOFOCUS_GAIN -> handleAudioFocusGain()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocus(audioFocusChangeListener)
    }

    private fun handleAudioFocusLoss() {
        audioTracks.forEach { it.player.pause() }
    }

    private fun handleAudioFocusLossTransient() {
        audioTracks.forEach { it.player.pause() }
    }

    private fun handleAudioFocusGain() {
        audioTracks.forEach { it.player.play() }
    }

    private fun sendEvent(eventName: String, params: Any?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    @ReactMethod
    fun multiply(a: Double, b: Double, promise: Promise) {
        promise.resolve(a * b + 1000)
    }

    @ReactMethod
    fun playAudio(promise: Promise) {
        if (requestAudioFocus()) {
            scope.launch {
                val latch = CountDownLatch(audioTracks.size)
                audioTracks.forEach { track ->
                    launch {
                        try {
                            withContext(Dispatchers.Main) {
                                track.player.setMediaItem(MediaItem.fromUri(Uri.parse(track.fileName)))
                                track.player.prepare()
                                latch.countDown()
                            }
                        } catch (e: Exception) {
                            promise.reject("PLAYBACK_ERROR", "Failed to prepare media item", e)
                        }
                    }
                }

                latch.await()
                withContext(Dispatchers.Main) {
                    val startTime = SystemClock.uptimeMillis() + 1000
                    audioTracks.forEach { track ->
                        track.player.seekTo(0)
                    }

                    val actualStartDelay = startTime - SystemClock.uptimeMillis()
                    if (actualStartDelay > 0) {
                        delay(actualStartDelay)
                    }

                    audioTracks.forEach { track ->
                        track.player.play()
                        startAmplitudeUpdate(track.fileName)
                    }

                    maxPlaybackDuration = audioTracks.maxOfOrNull { it.player.duration }?.toInt() ?: 0
                    startProgressUpdateTimer()
                }
                promise.resolve(true)
            }
        } else {
            promise.reject("AUDIO_FOCUS_ERROR", "Failed to gain audio focus")
        }
    }

    @ReactMethod
    fun downloadAudioFiles(urlStrings: ReadableArray, promise: Promise) {
        resetApp()
        sendEvent("DownloadStart", "DownloadStart")
        val urls = urlStrings.toArrayList().map { it.toString() }.map { URL(it) }

        val totalFiles = urls.size
        var downloadedFiles = 0
        var hasErrorOccurred = false

        scope.launch {
            val deferreds = urls.map { url ->
                async {
                    downloadFile(url)?.let { file ->
                        withContext(Dispatchers.Main) {
                            val exoPlayer = ExoPlayer.Builder(reactApplicationContext).build().apply {
                                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                                prepare()
                            }
                            audioTracks.add(AudioTrack(file.absolutePath, exoPlayer))
                            downloadedFiles += 1
                            downloadProgress = downloadedFiles.toDouble() / totalFiles
                            sendEvent("DownloadProgress", downloadProgress)
                        }
                    } ?: run {
                        hasErrorOccurred = true
                        sendEvent("DownloadErrors", "Failed to download file: ${url.path}")
                    }
                }
            }
            deferreds.awaitAll()
            withContext(Dispatchers.Main) {
                if (hasErrorOccurred) {
                    promise.reject("DOWNLOAD_ERROR", "Some files failed to download")
                } else {
                    sendEvent("DownloadComplete", audioTracks.map { it.fileName })
                    promise.resolve(true)
                }
            }
        }
    }

    @ReactMethod
    fun pauseResumeMix(promise: Promise) {
        isMixPaused = !isMixPaused

        if (isMixPaused) {
            audioTracks.forEach { track ->
                track.player.pause()
                pausedTime = track.player.currentPosition.toInt()
                playerDeviceCurrTime = SystemClock.uptimeMillis()
            }
        } else {
            val startDelay: Long = 1000
            val startTime = SystemClock.uptimeMillis()

            audioTracks.forEach { track ->
                track.player.seekTo(pausedTime.toLong())
            }

            val actualStartDelay = startTime + startDelay - SystemClock.uptimeMillis()
            if (actualStartDelay > 0) {
                scope.launch {
                    delay(actualStartDelay)
                    audioTracks.forEach { track ->
                        track.player.play()
                        startAmplitudeUpdate(track.fileName)
                    }
                }
            } else {
                audioTracks.forEach { track ->
                    track.player.play()
                    startAmplitudeUpdate(track.fileName)
                }
            }
        }
        promise.resolve(true)
    }

    @ReactMethod
    fun setVolume(volume: Float, forFileName: String, promise: Promise) {
        val track = audioTracks.find { it.fileName == forFileName }
        if (track != null) {
            track.player.volume = volume
            promise.resolve(true)
        } else {
            promise.reject("SET_VOLUME_ERROR", "Player does not exist for $forFileName")
        }
    }

    @ReactMethod
    fun setPan(pan: Float, forFileName: String, promise: Promise) {
        val track = audioTracks.find { it.fileName == forFileName }
        if (track != null) {
            val leftVolume = if (pan < 0) 1f else 1 - pan
            val rightVolume = if (pan > 0) 1f else 1 + pan
            track.player.volume = leftVolume
            promise.resolve(true)
        } else {
            promise.reject("SET_PAN_ERROR", "Player does not exist for $forFileName")
        }
    }

    @ReactMethod
    fun setPlaybackPosition(progress: Double, promise: Promise) {
        val newPosition = (progress * maxPlaybackDuration).toInt()
        audioTracks.forEach { track ->
            track.player.seekTo(newPosition.toLong())
        }
        pausedTime = newPosition
        promise.resolve(true)
    }

    private fun startAmplitudeUpdate(fileName: String) {
        amplitudeTimers[fileName]?.cancel()
        amplitudeTimers[fileName] = scope.launch {
            while (isActive) {
                updateAmplitude(fileName)
                delay(100)
            }
        }
    }

    private fun updateAmplitude(fileName: String) {
        val track = audioTracks.find { it.fileName == fileName }
        track?.let {
            val volume = it.volume
            val adjustedPower = it.player.volume * volume

            val amplitudes = (0 until 10).map { adjustedPower }
            sendEvent("TracksAmplitudes", amplitudes)
        }
    }

    private fun startProgressUpdateTimer() {
        scope.launch {
            while (true) {
                val currentPosition = audioTracks.firstOrNull()?.player?.currentPosition ?: 0
                val progress = currentPosition.toFloat() / maxPlaybackDuration
                sendEvent("PlaybackProgress", progress)
                delay(100)
            }
        }
    }

    private fun resetApp() {
        audioTracks.forEach { track ->
            track.player.release()
        }
        audioTracks.clear()
        downloadProgress = 0.0
        isMixPaused = false
        pausedTime = 0
        playerDeviceCurrTime = 0L
        amplitudeTimers.values.forEach { it.cancel() }
        amplitudeTimers.clear()
        abandonAudioFocus()
        sendEvent("AppReset", "AppReset")
    }

    private fun downloadFile(url: URL): File? {
        return try {
            val fileName = url.path.substring(url.path.lastIndexOf('/') + 1)
            val file = File(reactApplicationContext.cacheDir, fileName)
            url.openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
