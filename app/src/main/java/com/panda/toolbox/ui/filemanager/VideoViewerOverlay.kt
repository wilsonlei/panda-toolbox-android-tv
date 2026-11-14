package com.panda.toolbox.ui.filemanager

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.panda.toolbox.R
import com.panda.toolbox.model.FileItem
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

internal const val SEEK_STEP_MS = 10_000
internal const val OVERLAY_AUTO_HIDE_MS = 3_000L

@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun VideoViewerOverlay(
    item: FileItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(item.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
            playWhenReady = true
            prepare()
        }
    }
    var isPrepared by remember(item.path) { mutableStateOf(false) }
    var isPlaying by remember(item.path) { mutableStateOf(false) }
    var hasError by remember(item.path) { mutableStateOf(false) }
    var durationMs by remember(item.path) { mutableIntStateOf(0) }
    var positionMs by remember(item.path) { mutableIntStateOf(0) }
    var showOverlay by remember(item.path) { mutableStateOf(true) }
    var interactionTick by remember(item.path) { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    fun togglePlayback() {
        if (!isPrepared) return
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
        } else if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekBy(deltaMs: Int) {
        if (!isPrepared) return
        val maxPos = if (durationMs > 0) durationMs.toLong() else exoPlayer.duration.coerceAtLeast(0L)
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, maxPos)
        exoPlayer.seekTo(target)
        positionMs = target.toInt()
    }

    LaunchedEffect(showOverlay, interactionTick) {
        if (showOverlay) {
            delay(OVERLAY_AUTO_HIDE_MS)
            showOverlay = false
        }
    }

    LaunchedEffect(showOverlay, isPrepared) {
        if (showOverlay && isPrepared) {
            runCatching { playPauseFocusRequester.requestFocus() }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isPrepared, isPlaying, item.path) {
        while (isPrepared && isPlaying) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L).toInt()
            delay(500L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPrepared = true
                        hasError = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L).toInt()
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        positionMs = durationMs
                    }
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isPrepared = false
                isPlaying = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.key == Key.Back || event.key == Key.Escape) {
                            onClose()
                            return@onPreviewKeyEvent true
                        }
                        val isEnter = event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter
                        if (!showOverlay && isEnter) {
                            togglePlayback()
                            return@onPreviewKeyEvent true
                        }
                        val isRestoreKey = event.key == Key.DirectionUp ||
                            event.key == Key.DirectionDown
                        if (!showOverlay && isRestoreKey) {
                            showOverlay = true
                            interactionTick++
                            return@onPreviewKeyEvent true
                        }
                        if (showOverlay) {
                            interactionTick++
                        }
                    }
                    false
                }
        ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = exoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        if (!isPrepared && !hasError) {
            Text(
                text = stringResource(R.string.loading_text),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        if (hasError) {
            Text(
                text = stringResource(R.string.video_load_error),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Top Title Bar
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }
        }

        // Bottom Playback Controls
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
            val progressFraction = if (durationMs > 0) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                OutlinedButton(
                    onClick = { seekBy(-SEEK_STEP_MS) },
                    enabled = isPrepared
                ) {
                    Text(text = stringResource(R.string.rewind_10s))
                }

                Button(
                    onClick = { togglePlayback() },
                    enabled = isPrepared,
                    modifier = Modifier.focusRequester(playPauseFocusRequester)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = stringResource(
                            if (isPlaying) R.string.pause_action else R.string.play_action
                        ),
                        modifier = Modifier.size(22.dp)
                    )
                }

                OutlinedButton(
                    onClick = { seekBy(SEEK_STEP_MS) },
                    enabled = isPrepared
                ) {
                    Text(text = stringResource(R.string.forward_10s))
                }
            }
            }
        }
    }
    }
}

internal fun formatDuration(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
