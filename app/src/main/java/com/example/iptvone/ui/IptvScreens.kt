package com.example.iptvone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.example.iptvone.model.Channel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListScreen(
    channels: List<Channel>,
    listState: LazyListState,
    selectedChannelUrl: String?,
    onChannelSelected: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusChannelUrl = selectedChannelUrl ?: channels.firstOrNull()?.url

    LaunchedEffect(focusChannelUrl, channels.size) {
        if (channels.isEmpty()) return@LaunchedEffect
        val index = channels.indexOfFirst { it.url == focusChannelUrl }.coerceAtLeast(0)
        listState.scrollToItem(index)
        focusRequester.requestFocus()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Kanali (${channels.size})",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.url }) { channel ->
                Surface(
                    onClick = { onChannelSelected(channel) },
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                        .then(
                            if (channel.url == focusChannelUrl) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Text(
                        text = channel.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    isBuffering: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoPlayer(
            exoPlayer = exoPlayer,
            modifier = Modifier.fillMaxSize()
        )

        // Loading/Error overlay
        if (isBuffering || errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBuffering && errorMessage == null) {
                        Text(text = "Učitavanje…", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                    errorMessage?.let { msg ->
                        Text(text = msg, color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Text(
            text = channel.name,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
