package com.armsaudio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch

data class AudioTrack(
    val fileName: String,
    val player: MediaPlayer,
    var volume: Float = 1.0f,
    var pan: Float = 0.0f,
    var amplitudes: MutableList<Float> = MutableList(10) { 0.0f } // Initialize with 10 zeroes
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
    private var progressUpdateTimer: Job? = null

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
        audioTracks.forEach { it.player.start() }
    }

    private fun sendEvent(eventName: String, params: Any?) {
        try {
            val reactContext = reactApplicationContext
            val eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)

            when (params) {
                null -> eventEmitter.emit(eventName, null)
                is String -> eventEmitter.emit(eventName, params)
                is WritableMap -> eventEmitter.emit(eventName, params)
                is WritableArray -> eventEmitter.emit(eventName, params)
                else -> {
                    // Convert other types to a JSON string as a fallback
                    eventEmitter.emit(eventName, params.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendArrayEvent(eventName: String, list: List<Any>) {
        val array = Arguments.createArray()
        list.forEach { item ->
            when (item) {
                is String -> array.pushString(item)
                is Int -> array.pushInt(item)
                is Double -> array.pushDouble(item)
                is Boolean -> array.pushBoolean(item)
                // Add more types as needed
                else -> array.pushString(item.toString())
            }
        }
        sendEvent(eventName, array)
    }

    private fun sendMapEvent(eventName: String, map: Map<String, Any>) {
        val writableMap = Arguments.createMap()
        map.forEach { (key, value) ->
            when (value) {
                is String -> writableMap.putString(key, value)
                is Int -> writableMap.putInt(key, value)
                is Double -> writableMap.putDouble(key, value)
                is Boolean -> writableMap.putBoolean(key, value)
                // Add more types as needed
                else -> writableMap.putString(key, value.toString())
            }
        }
        sendEvent(eventName, writableMap)
    }

    private fun sendGenAppErrors(errors: String) {
        val errorEvent = Arguments.createMap()
        errorEvent.putString("errMsg", errors)
        sendEvent("AppErrorsX", errorEvent)
    }

    @ReactMethod
    private fun playAudio() {
        if (requestAudioFocus()) {
            val latch = CountDownLatch(audioTracks.size)
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                audioTracks.forEach { track ->
                    launch {
                        try {
                            withContext(Dispatchers.Main) {
                                track.player.reset()
                                track.player.setDataSource(track.fileName)
                                track.player.prepareAsync()
                                track.player.setOnPreparedListener {
                                    latch.countDown()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sendGenAppErrors("Error preparing player for ${track.fileName}: ${e.localizedMessage}")
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
                        track.player.start()
                        startAmplitudeUpdate(track.fileName)
                    }

                    maxPlaybackDuration = audioTracks.maxOfOrNull { it.player.duration } ?: 0
                    startProgressUpdateTimer()
                }
            }
        } else {
            sendGenAppErrors("Failed to gain audio focus")
        }
    }

    @ReactMethod
    fun downloadAudioFiles(urlStrings: ReadableArray) {
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
                            val mediaPlayer = MediaPlayer().apply {
                                setDataSource(reactApplicationContext, Uri.fromFile(file))
                                prepare()
                            }
                            audioTracks.add(AudioTrack(file.absolutePath, mediaPlayer))
                            downloadedFiles += 1
                            downloadProgress = downloadedFiles.toDouble() / totalFiles
                            
                            val progressEvent = Arguments.createMap()
                            progressEvent.putDouble("progress", downloadProgress)
                            sendEvent("DownloadProgress", progressEvent)
                        }
                    } ?: run {
                        hasErrorOccurred = true
                        sendGenAppErrors("Failed to download file: ${url.path}")
                    }
                }
            }
            deferreds.awaitAll()
            withContext(Dispatchers.Main) {
                if (!hasErrorOccurred) {
                    sendArrayEvent("DownloadComplete", audioTracks.map { it.fileName })
                } else {
                    resetApp()
                }
            }
        }
    }

    @ReactMethod
    fun pauseResumeMix() {
        isMixPaused = !isMixPaused

        if (isMixPaused) {
            audioTracks.forEach { track ->
                track.player.pause()
                pausedTime = track.player.currentPosition
                playerDeviceCurrTime = SystemClock.uptimeMillis()
                amplitudeTimers[track.fileName]?.cancel() // Stop amplitude update
            }
            progressUpdateTimer?.cancel() // Stop progress update
        } else {
            val startDelay: Long = 1000
            val startTime = SystemClock.uptimeMillis()

            audioTracks.forEach { track ->
                track.player.seekTo(pausedTime)
            }

            val actualStartDelay = startTime + startDelay - SystemClock.uptimeMillis()
            if (actualStartDelay > 0) {
                scope.launch {
                    delay(actualStartDelay)
                    audioTracks.forEach { track ->
                        track.player.start()
                        startAmplitudeUpdate(track.fileName) // Start amplitude update
                    }
                }
            } else {
                audioTracks.forEach { track ->
                    track.player.start()
                    startAmplitudeUpdate(track.fileName) // Start amplitude update
                }
            }

            startProgressUpdateTimer()
        }
    }

    @ReactMethod
    fun setVolume(volume: Float, forFileName: String, promise: Promise) {
        val track = audioTracks.find { it.fileName == forFileName }
        if (track != null) {
            track.player.setVolume(volume, volume)
            track.volume = volume
            promise.resolve(true)
        } else {
            promise.reject("SET_VOLUME_ERROR", "Player does not exist for $forFileName")
            sendGenAppErrors("Player does not exist for $forFileName")
        }
    }

    @ReactMethod
    fun setPan(pan: Float, forFileName: String, promise: Promise) {
        val track = audioTracks.find { it.fileName == forFileName }
        if (track != null) {
            val leftVolume = if (pan < 0) 1f else 1 - pan
            val rightVolume = if (pan > 0) 1f else 1 + pan
            track.player.setVolume(leftVolume, rightVolume)
            track.pan = pan
            promise.resolve(true)
        } else {
            promise.reject("SET_PAN_ERROR", "Player does not exist for $forFileName")
            sendGenAppErrors("Player does not exist for $forFileName")
        }
    }

    @ReactMethod
    fun setAudioProgress(progress: Double, promise: Promise) {
        // Pause the mix
        if (!isMixPaused) {
            pauseResumeMix()
        }
    
        // Seek to the new position
        val newPosition = (progress * maxPlaybackDuration).toInt()
        audioTracks.forEach { track ->
            track.player.seekTo(newPosition)
        }
        pausedTime = newPosition
    
        promise.resolve(true)
    }

    @ReactMethod
    fun audioSliderChanged(progress: Double, promise: Promise) {
        // Set the new position
        val newPosition = (progress * maxPlaybackDuration).toInt()
        audioTracks.forEach { track ->
            track.player.seekTo(newPosition)
        }
        pausedTime = newPosition
    
        // Resume the mix
        if (isMixPaused) {
            val startDelay: Long = 10
            val startTime = SystemClock.uptimeMillis()
    
            val actualStartDelay = startTime + startDelay - SystemClock.uptimeMillis()
            if (actualStartDelay > 0) {
                scope.launch {
                    delay(actualStartDelay)
                    pauseResumeMix() // Resume the mix
                }
            } else {
                pauseResumeMix() // Resume the mix immediately
            }
        }
    
        promise.resolve(true)
    }

    private fun startAmplitudeUpdate(fileName: String) {
        amplitudeTimers[fileName]?.cancel()
        amplitudeTimers[fileName] = scope.launch {
            while (isActive) {
                updateAmplitude(fileName)
                delay(100) // Update every 100ms
            }
        }
    }

    private fun getAmplitudeFromPlayer(player: MediaPlayer, volume: Float): Float {
        // Generate a random amplitude value and scale it by the track's volume
        val amplitude = (0..100).random().toFloat() / 100
        return amplitude * volume
    }
    
    private fun updateAmplitude(fileName: String) {
        if (isMixPaused) return
    
        val track = audioTracks.find { it.fileName == fileName }
        track?.let {
            val amplitude = getAmplitudeFromPlayer(it.player, it.volume)
            val adjustedPower = amplitude
    
            it.amplitudes.removeAt(0)
            it.amplitudes.add(adjustedPower)
    
            sendTrackAmplitudeUpdate(it.fileName, it.amplitudes)
        }
    }

    private fun sendTrackAmplitudeUpdate(fileName: String, amplitudes: List<Float>) {
        val eventParams = Arguments.createMap()
        eventParams.putString("fileName", fileName)
        val amplitudeArray = Arguments.createArray()
        amplitudes.forEach { amplitudeArray.pushDouble(it.toDouble()) }
        eventParams.putArray("amplitudes", amplitudeArray)
        sendEvent("TracksAmplitudes", eventParams)
    }

    private fun startProgressUpdateTimer() {
        progressUpdateTimer?.cancel() // Ensure no duplicate timers
        progressUpdateTimer = scope.launch {
            while (isActive && !isMixPaused) {
                val currentPosition = audioTracks.firstOrNull()?.player?.currentPosition ?: 0
                val progress = currentPosition.toFloat() / maxPlaybackDuration
                val progressEvent = Arguments.createMap()
                progressEvent.putDouble("progress", progress.toDouble())
                sendEvent("PlaybackProgress", progressEvent)
                delay(100) // Update every 100ms
            }
        }
    }

    @ReactMethod
private fun resetApp() {
    // Stop and release all media players
    audioTracks.forEach { track ->
        track.player.stop()
        track.player.release()
    }
    audioTracks.clear()

    // Reset internal state variables
    downloadProgress = 0.0
    isMixPaused = false
    pausedTime = 0
    playerDeviceCurrTime = 0L
    maxPlaybackDuration = 0

    // Cancel and clear amplitude update timers
    amplitudeTimers.values.forEach { it.cancel() }
    amplitudeTimers.clear()

    // Cancel progress update timer
    progressUpdateTimer?.cancel()
    progressUpdateTimer = null

    // Abandon audio focus
    abandonAudioFocus()

    // Notify React Native about the reset
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
            sendGenAppErrors("Error downloading file: ${e.localizedMessage}")
            null
        }
    }
}
