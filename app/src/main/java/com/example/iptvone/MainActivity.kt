package com.example.iptvone

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.iptvone.model.Channel
import com.example.iptvone.parser.M3uParser
import com.example.iptvone.player.ExoPlayerFactory
import com.example.iptvone.ui.ChannelListScreen
import com.example.iptvone.ui.PlayerScreen
import com.example.iptvone.ui.theme.IptvOneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen {
    List,
    Player
}

@UnstableApi
class MainActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var wasPlayingBeforeStop = false
    private var onChannelChange: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exoPlayer = ExoPlayerFactory.create(this)

        setContent {
            exoPlayer?.let { player ->
                IptvApp(
                    exoPlayer = player,
                    registerChannelChangeCallback = { callback ->
                        onChannelChange = callback
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (onChannelChange != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                    onChannelChange?.invoke(true)
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    onChannelChange?.invoke(false)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        if (wasPlayingBeforeStop) {
            exoPlayer?.play()
        }
    }

    override fun onStop() {
        wasPlayingBeforeStop = exoPlayer?.isPlaying == true
        exoPlayer?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IptvApp(exoPlayer: ExoPlayer, registerChannelChangeCallback: ((Boolean) -> Unit) -> Unit) {
    IptvOneTheme {
        IptvAppContent(
            exoPlayer = exoPlayer,
            registerChannelChangeCallback = registerChannelChangeCallback
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
private fun IptvAppContent(exoPlayer: ExoPlayer, registerChannelChangeCallback: ((Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE) }

    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var currentScreen by remember { mutableStateOf(Screen.List) }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // State for player UI
    var playerHasError by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    var playerIsBuffering by remember { mutableStateOf(false) }

    // Global Player Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerHasError = true
                playerIsBuffering = false
                playerErrorMessage = "Greška: ${error.localizedMessage}. Pokušavam ponovo..."
                
                // Auto-retry
                scope.launch {
                    delay(3000)
                    if (currentScreen == Screen.Player) {
                        selectedChannel?.let { channel ->
                            exoPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(channel.url))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerIsBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerHasError = false
                    playerErrorMessage = null
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        channels = loadChannelsFromAssets(context, "playlist.m3u")

        val lastUrl = sharedPrefs.getString("last_channel_url", null)
        if (lastUrl != null) {
            channels.find { it.url == lastUrl }?.let { channel ->
                selectedChannel = channel
            }
        }

        isLoading = false
    }

    // Remote control channel change
    LaunchedEffect(channels, selectedChannel, currentScreen) {
        if (currentScreen == Screen.Player && channels.isNotEmpty()) {
            registerChannelChangeCallback { isNext ->
                val currentIndex = channels.indexOfFirst { it.url == selectedChannel?.url }
                if (currentIndex != -1) {
                    val nextIndex = if (isNext) {
                        (currentIndex + 1) % channels.size
                    } else {
                        (currentIndex - 1 + channels.size) % channels.size
                    }
                    selectedChannel = channels[nextIndex]
                }
            }
        } else {
            registerChannelChangeCallback {}
        }
    }

    // Playback control
    LaunchedEffect(selectedChannel, currentScreen) {
        if (currentScreen != Screen.Player) return@LaunchedEffect
        
        selectedChannel?.let { channel ->
            sharedPrefs.edit().putString("last_channel_url", channel.url).apply()
            
            playerHasError = false
            playerIsBuffering = true
            
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(ExoPlayerFactory.buildMediaItem(channel.url))
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    BackHandler(enabled = currentScreen == Screen.Player) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentScreen = Screen.List
    }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Učitavanje kanala…",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        currentScreen == Screen.List -> {
            ChannelListScreen(
                channels = channels,
                listState = listState,
                selectedChannelUrl = selectedChannel?.url,
                onChannelSelected = { channel ->
                    selectedChannel = channel
                    currentScreen = Screen.Player
                }
            )
        }
        currentScreen == Screen.Player && selectedChannel != null -> {
            PlayerScreen(
                channel = selectedChannel!!,
                exoPlayer = exoPlayer,
                isBuffering = playerIsBuffering,
                errorMessage = if (playerHasError) playerErrorMessage else null
            )
        }
    }
}

private suspend fun loadChannelsFromAssets(context: Context, fileName: String): List<Channel> =
    withContext(Dispatchers.IO) {
        try {
            context.assets.open(fileName).use { inputStream ->
                M3uParser.parse(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
