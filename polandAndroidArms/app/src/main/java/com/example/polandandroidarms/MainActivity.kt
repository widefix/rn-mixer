package com.example.polandandroidarms

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.polandandroidarms.ui.theme.PolandAndroidArmsTheme
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

data class AudioTrack(
    val fileName: String,
    val player: MediaPlayer,
    var volume: Float = 1.0f, // Default volume
    var pan: Float = 0.0f     // Default pan (0 is centered)
)

class MainActivity : ComponentActivity() {

    private val audioFileURLsList: List<URL> = listOf(
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/236/original/Way_Maker__0_-_E_-_Original_--_11-Lead_Vox.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/227/original/Way_Maker__0_-_E_-_Original_--_3-Drums.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/229/original/Way_Maker__0_-_E_-_Original_--_4-Percussion.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/232/original/Way_Maker__0_-_E_-_Original_--_5-Bass.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/230/original/Way_Maker__0_-_E_-_Original_--_6-Acoustic.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/226/original/Way_Maker__0_-_E_-_Original_--_1-Click.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/228/original/Way_Maker__0_-_E_-_Original_--_2-Guide.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/234/original/Way_Maker__0_-_E_-_Original_--_7-Electric_1.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/235/original/Way_Maker__0_-_E_-_Original_--_8-Electric_2.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/237/original/Way_Maker__0_-_E_-_Original_--_9-Main_Keys.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/231/original/Way_Maker__0_-_E_-_Original_--_10-Aux_Keys.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/238/original/Way_Maker__0_-_E_-_Original_--_12-Soprano_Vox.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/239/original/Way_Maker__0_-_E_-_Original_--_13-Tenor_Vox.m4a"),
        URL("https://cdn.worshiponline.com/estp-public/song_audio_mixer_tracks/audios/000/034/233/original/Way_Maker__0_-_E_-_Original_--_14-Choir.m4a"),

    )

    private var isMixPaused = false
    private var pausedTime = 0
    private var playerDeviceCurrTime = 0L

    private val pickAudioFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Handle the selected audio file here
        }
    }

    private var audioTracks = mutableStateListOf<AudioTrack>()
    private var downloadProgress by mutableDoubleStateOf(0.0)
    private var isMixBtnClicked by mutableStateOf(false)
    private var isMasterControlShowing by mutableStateOf(false)
    private var playbackProgress by mutableFloatStateOf(0f)
    private var maxPlaybackDuration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PolandAndroidArmsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun handlePlayMix() {
        startProgressUpdateTimer()
        isMixBtnClicked = false

        val latch = CountDownLatch(audioTracks.size)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            // Prepare all tracks asynchronously
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
                    }
                }
            }

            // Wait for all tracks to be prepared
            latch.await()

            withContext(Dispatchers.Main) {
                // Ensure all players start at the calculated start time
                val startTime = SystemClock.uptimeMillis() + 1000 // 1 second delay to ensure readiness

                // Seek to the start position for all tracks
                audioTracks.forEach { track ->
                    track.player.seekTo(0)
                }

                // Calculate the actual delay needed to start all players at the same time
                val actualStartDelay = startTime - SystemClock.uptimeMillis()
                if (actualStartDelay > 0) {
                    delay(actualStartDelay)
                }

                // Start all players
                audioTracks.forEach { track ->
                    track.player.start()
                    startAmplitudeUpdate(track.fileName)
                }

                // Update the max playback duration
                maxPlaybackDuration = audioTracks.maxOfOrNull { it.player.duration } ?: 0
                startPlaybackProgressUpdater()
            }
        }

        isMasterControlShowing = true
    }

    private fun handleDownloadTracks() {
        resetApp()
        val urls = audioFileURLsList
        val totalFiles = urls.size
        var downloadedFiles = 0

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val deferreds = urls.map { url ->
                async {
                    downloadFile(url)?.let { file ->
                        withContext(Dispatchers.Main) {
                            val player = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                prepare()
                            }
                            audioTracks.add(AudioTrack(file.absolutePath, player))
                            getAudioProperties(url)
                            downloadedFiles += 1
                            downloadProgress = downloadedFiles.toDouble() / totalFiles
                        }
                    }
                }
            }
            deferreds.awaitAll()
            withContext(Dispatchers.Main) {
                isMixBtnClicked = true
                downloadProgress = 1.0
            }
        }
    }

    private fun handleResumePauseMix() {
        // Handle resumePauseMix action
        isMixPaused = !isMixPaused

        if (isMixPaused) {
            audioTracks.forEach { track ->
                track.player.pause()
                pausedTime = track.player.currentPosition
                playerDeviceCurrTime = SystemClock.uptimeMillis()
            }
            println("All players paused successfully at $pausedTime ms with device time $playerDeviceCurrTime ms")
        } else {
            val startDelay: Long = 1000 // Delay to ensure all players are ready
            val startTime = SystemClock.uptimeMillis()

            audioTracks.forEach { track ->
                track.player.seekTo(pausedTime)
            }

            // Calculate the actual delay needed to start all players at the same time
            val actualStartDelay = startTime + startDelay - SystemClock.uptimeMillis()
            if (actualStartDelay > 0) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(actualStartDelay)
                    audioTracks.forEach { track ->
                        track.player.start()
                        startAmplitudeUpdate(track.fileName)
                    }
                }
            } else {
                audioTracks.forEach { track ->
                    track.player.start()
                    startAmplitudeUpdate(track.fileName)
                }
            }
        }
    }

    private fun handlePickAudioMix() {
        pickAudioFileLauncher.launch("audio/*")
    }

    private fun resetApp() {
        // Reset application state
        // Release and clear all media players
        audioTracks.forEach { track ->
            track.player.release()
        }
        audioTracks.clear()

        // Reset download progress
        downloadProgress = 0.0

        // Reset boolean flags
        isMixBtnClicked = false
        isMasterControlShowing = false
        isMixPaused = false

        // Reset paused time and current player time
        pausedTime = 0
        playerDeviceCurrTime = 0L
    }

    private fun getAudioProperties(url: URL) {
        // Implement logic to get audio properties
    }

    private fun downloadFile(url: URL): File? {
        return try {
            val fileName = url.path.substring(url.path.lastIndexOf('/') + 1)
            val file = File(cacheDir, fileName)
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

    private fun startAmplitudeUpdate(fileName: String) {
        // Implement logic to start updating amplitude
    }

    private fun startProgressUpdateTimer() {
        // Implement logic to start updating progress
    }

    private fun startPlaybackProgressUpdater() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                val currentPosition = audioTracks.firstOrNull()?.player?.currentPosition ?: 0
                playbackProgress = currentPosition / maxPlaybackDuration.toFloat()
                delay(100) // Update every 100ms
            }
        }
    }

    private fun handleSeekToProgress(progress: Float) {
        val newPosition = (progress * maxPlaybackDuration).toInt()
        audioTracks.forEach { track ->
            track.player.seekTo(newPosition)
        }
        playbackProgress = progress
    }

    @Composable
    fun MainContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Greeting(name = "Android")
            PlaybackSlider()
            Button(onClick = { handlePlayMix() }) {
                Text(text = "Play Mix")
            }
            Button(onClick = { handleDownloadTracks() }) {
                Text(text = "Download Tracks")
            }
            Button(onClick = { handleResumePauseMix() }) {
                Text(text = "Resume/Pause Mix")
            }
            Button(onClick = { handlePickAudioMix() }) {
                Text(text = "Pick Audio Mix")
            }
            Text(text = "Download Progress: ${(downloadProgress * 100).toInt()}%")

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(audioTracks) { index, track ->
                    var volume by remember { mutableFloatStateOf(track.volume) }
                    var pan by remember { mutableFloatStateOf(track.pan) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "${index + 1}. ${track.fileName.substringAfterLast("/")}")
                        Slider(
                            value = volume,
                            onValueChange = { newVolume ->
                                volume = newVolume
                                track.volume = newVolume
                                track.player.setVolume(newVolume, newVolume)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(text = "Volume: ${(volume * 100).toInt()}%")
                        Slider(
                            value = pan,
                            onValueChange = { newPan ->
                                pan = newPan
                                track.pan = newPan
                                // Adjust pan (left-right balance) here
                                val leftVolume = if (newPan < 0) 1f else 1 - newPan
                                val rightVolume = if (newPan > 0) 1f else 1 + newPan
                                track.player.setVolume(leftVolume * volume, rightVolume * volume)
                            },
                            valueRange = -1f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(text = "Pan: $pan")
                    }
                }
            }
        }
    }

    @Composable
    fun PlaybackSlider() {
        val context = LocalContext.current

        var isNowProgress by remember(playbackProgress) { mutableFloatStateOf(playbackProgress) }
        var isTracking by remember { mutableStateOf(false) }

        Text(text = "Mix Playback", color = Color(0xFF191970))
        Slider(
            value = isNowProgress,
            onValueChange = { newVal ->
                isNowProgress = newVal
                playbackProgress = newVal
                if (!isTracking) {
                    Toast.makeText(context, "Started tracking touch ${(newVal * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                    isTracking = true
                    handleResumePauseMix()
                }
                Toast.makeText(context, "Current progress: ${(newVal * 100).toInt()}%", Toast.LENGTH_SHORT).show()
            },
            onValueChangeFinished = {
                Toast.makeText(context, "Stopped tracking touch", Toast.LENGTH_SHORT).show()
                isTracking = false
                handleSeekToProgress(playbackProgress)
                handleResumePauseMix()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(8.dp),
            enabled = true
        )
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        PolandAndroidArmsTheme {
            Greeting("Android")
        }
    }
}
