package com.armsaudio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.ReactConstants.TAG
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch

data class AudioTrack(
    val fileName: String,
    val internalTrackNumber: Int,
    var volume: Float = 1.0f,
    var pan: Float = 0.0f,
    var amplitudes: MutableList<Float> = MutableList(10) { 0.0f } // Initialize with 10 zeroes
)

class ArmsaudioModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "Armsaudio"
    }

    init {
        System.loadLibrary("sound")
    }

    external fun testFunction(): Long
    external fun preparePlayer()
    external fun resetPlayer()
    external fun loadTrack(fileName: String): Int
    external fun playAudioInternal()
    external fun pauseAudio()
    external fun resumeAudio()
    external fun getCurrentPosition(): Float
    external fun getAmplitudes(): Array<Float>
    external fun setPosition(position: Float)
    external fun setTrackVolume(trackNum: Int, volume: Float)
    external fun setTrackPan(trackNum: Int, pan: Float)

    override fun getName(): String {
        return NAME
    }

    private var isMixPaused = false
    private var playerDeviceCurrTime = 0L
    private var audioTracks = mutableListOf<AudioTrack>()
    private var downloadProgress = 0.0
    private var maxPlaybackDuration = 0
    private val audioManager: AudioManager by lazy {
        reactApplicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var amplitudeTimer: Job? = null
    private var progressUpdateTimer: Job? = null
    private var playerPrepared = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> handleAudioFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> handleAudioFocusLossTransient()
            AudioManager.AUDIOFOCUS_GAIN -> handleAudioFocusGain()
        }
    }

    private val lifecycleEventListener = object : LifecycleEventListener {
        override fun onHostResume() = resumeAudio()
        override fun onHostPause() = pauseAudio()
        override fun onHostDestroy() = pauseAudio()
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
        pauseAudio()
    }

    private fun handleAudioFocusLossTransient() {
        pauseAudio()
    }

    private fun handleAudioFocusGain() {
        resumeAudio()
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

    private fun prepareAudioPlayer() {
        preparePlayer()
    }

    @ReactMethod
    fun addListener(type: String?) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    fun removeListeners(type: Int?) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    private fun testLibrary() {
        val expected = 17L
        val actual = testFunction()

        val testResultEvent = Arguments.createMap()
        testResultEvent.putBoolean("cpp link success", expected == actual)
        sendEvent("Library link: true", testResultEvent)
    }

    @ReactMethod
    private fun playAudio() {
        if (requestAudioFocus()) {
            reactApplicationContext.addLifecycleEventListener(lifecycleEventListener);
            playAudioInternal()
            startAmplitudeUpdate()
            startProgressUpdateTimer()
        } else {
            sendGenAppErrors("Failed to gain audio focus")
        }
    }

    @ReactMethod
    fun downloadAudioFiles(urlStrings: ReadableArray) {
        resetApp()
        sendEvent("DownloadStart", "DownloadStart")
        if (!playerPrepared) {
            preparePlayer()
            playerPrepared = true
        }

        val urls = urlStrings.toArrayList().map { it.toString() }.map { URL(it) }

        val totalActions = urls.size
        var doneActions = 0
        var hasErrorOccurred = false

        scope.launch {
            val deferreds = urls.map { url ->
                async(Dispatchers.IO) {  // Run the heavy operations on the IO dispatcher
                    val file = downloadFile(url)
                    if (file == null) {
                        hasErrorOccurred = true
                        sendGenAppErrors("Failed to download file: ${url.path}")
                    } else {
                        convertAndAddTrack(file)  // Process the file
                    }
                    file
                }
            }

            deferreds.forEach { deferred ->
                val file = deferred.await()  // Wait for the file to be processed

                // Once each file is done, update the UI on the main thread
                withContext(Dispatchers.Main) {
                    doneActions += 1
                    downloadProgress = doneActions.toDouble() / totalActions

                    val progressEvent = Arguments.createMap()
                    progressEvent.putDouble("progress", downloadProgress)
                    sendEvent("DownloadProgress", progressEvent)
                }
            }

            withContext(Dispatchers.Main) {
                if (!hasErrorOccurred)
                    sendArrayEvent("DownloadComplete", audioTracks.map { it.fileName })
                else resetApp()
            }
        }
    }

    @ReactMethod
    fun pauseResumeMix() {
        isMixPaused = !isMixPaused

        if (isMixPaused) {
            pauseAudio()
            audioTracks.forEach { track ->
                playerDeviceCurrTime = SystemClock.uptimeMillis()
                amplitudeTimer?.cancel() // Stop amplitude update
            }

            progressUpdateTimer?.cancel() // Stop progress update
        } else {
            resumeAudio()
            startAmplitudeUpdate()
            startProgressUpdateTimer()
        }
    }

    @ReactMethod
    fun setVolume(volume: Float, forFileName: String, promise: Promise) {
        val track = audioTracks.find { it.fileName == forFileName }
        if (track != null) {
            track.volume = volume
            setTrackVolume(track.internalTrackNumber, volume)
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
            track.pan = pan
            setTrackPan(track.internalTrackNumber, pan)
            promise.resolve(true)
        } else {
            promise.reject("SET_PAN_ERROR", "Player does not exist for $forFileName")
            sendGenAppErrors("Player does not exist for $forFileName")
        }
    }

    @ReactMethod
    fun setAudioProgress(progress: Double, promise: Promise) {
        // Pause the mix
        if (!isMixPaused) pauseResumeMix()

        // Seek to the new position
        setPosition(progress.toFloat())

        promise.resolve(true)
    }

    @ReactMethod
    fun audioSliderChanged(progress: Double, promise: Promise) {
        // Set the new position
        setPosition(progress.toFloat())

        // Resume the mix
        pauseResumeMix()
        promise.resolve(true)
    }

    private fun startAmplitudeUpdate() {
        amplitudeTimer?.cancel()
        amplitudeTimer = scope.launch {
            while (isActive) {
                updateAmplitudes()
                delay(100) // Update every 100ms
            }
        }
    }

    private fun updateAmplitudes() {
        if (isMixPaused) return

        val amplitudes = getAmplitudes()
        audioTracks.forEachIndexed { index, track ->
            track.amplitudes.removeAt(0)
            track.amplitudes.add(amplitudes[index])
        }

        sendTrackAmplitudeUpdates()
    }

    private fun sendTrackAmplitudeUpdates() {
        val eventParams = Arguments.createMap()
        val amplitudesMap = Arguments.createMap()
        audioTracks.forEach {
            amplitudesMap.putString(it.fileName, it.amplitudes.last().toString())
        }

        eventParams.putMap("amplitudes", amplitudesMap)
        sendEvent("TracksAmplitudes", eventParams)
    }

    private fun startProgressUpdateTimer() {
        progressUpdateTimer?.cancel() // Ensure no duplicate timers
        progressUpdateTimer = scope.launch {
            while (isActive && !isMixPaused) {
                val progress = getCurrentPosition()
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
        resetPlayer()
        reactApplicationContext.removeLifecycleEventListener(lifecycleEventListener)
        deleteCache(reactApplicationContext)
        audioTracks.clear()

        // Reset internal state variables
        downloadProgress = 0.0
        isMixPaused = false
        playerDeviceCurrTime = 0L
        maxPlaybackDuration = 0

        // Cancel and clear amplitude update timers
        amplitudeTimer?.cancel()
        amplitudeTimer = null

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

    private fun convertAndAddTrack(file: File) {
        val i = file.name.lastIndexOf('.')
        val substr = file.name.substring(0, i)
        val outputFile = File(file.parent, "$substr.wav")
        if (outputFile.exists() && outputFile.totalSpace > 0)
            addTrack(outputFile)
        else convertFile(file, outputFile) { addTrack(it) }
    }

    private fun addTrack(track: File) {
        val trackNum = loadTrack(track.absolutePath)
        audioTracks.add(AudioTrack(track.absolutePath, trackNum))
    }

    private fun deleteCache(context: Context) {
        try {
            val dir = context.cacheDir
            deleteDir(dir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            for (i in children.indices) {
                val success = deleteDir(File(dir, children[i]))
                if (!success) {
                    return false
                }
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        } else {
            return false
        }
    }

    private fun convertFile(
        inputFile: File,
        outputFile: File,
        outputFileHandler: (File) -> Unit
    ) {
        val session = FFmpegKit.execute("-i $inputFile -ar 48000 $outputFile")
        if (ReturnCode.isSuccess(session.returnCode)) {
            outputFileHandler.invoke(outputFile)
        } else if (ReturnCode.isCancel(session.returnCode)) {
            // CANCEL
        } else {
            Log.d(
                TAG,
                String.format(
                    "Command failed with state %s and rc %s.%s",
                    session.state,
                    session.returnCode,
                    session.failStackTrace
                )
            )
        }
    }
}
