package com.panda.toolbox.ui.filemanager

import android.media.MediaPlayer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.panda.toolbox.R
import com.panda.toolbox.model.FileItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudioViewerOverlay(
    item: FileItem,
    onClose: () -> Unit
) {
    var mediaPlayerRef by remember(item.path) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(item.path) { mutableStateOf(false) }
    var isPlaying by remember(item.path) { mutableStateOf(false) }
    var hasError by remember(item.path) { mutableStateOf(false) }
    var durationMs by remember(item.path) { mutableIntStateOf(0) }
    var positionMs by remember(item.path) { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    fun togglePlayback() {
        val mp = mediaPlayerRef ?: return
        if (!isPrepared) return
        if (mp.isPlaying) {
            mp.pause()
            isPlaying = false
        } else {
            mp.start()
            isPlaying = true
        }
    }

    fun seekBy(deltaMs: Int) {
        val mp = mediaPlayerRef ?: return
        if (!isPrepared) return
        val maxPos = if (durationMs > 0) durationMs else mp.duration.coerceAtLeast(0)
        val target = (mp.currentPosition + deltaMs).coerceIn(0, maxPos)
        mp.seekTo(target)
        positionMs = target
    }

    LaunchedEffect(isPrepared) {
        if (isPrepared) {
            runCatching { playPauseFocusRequester.requestFocus() }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isPrepared, isPlaying, item.path) {
        while (isPrepared && isPlaying) {
            positionMs = mediaPlayerRef?.currentPosition?.coerceAtLeast(0) ?: positionMs
            delay(500L)
        }
    }

    DisposableEffect(item.path) {
        val mp = MediaPlayer()
        mediaPlayerRef = mp
        try {
            mp.setDataSource(item.path)
            mp.setOnPreparedListener { prepared ->
                isPrepared = true
                hasError = false
                durationMs = prepared.duration.coerceAtLeast(0)
                prepared.start()
                isPlaying = true
            }
            mp.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
            mp.setOnErrorListener { _, _, _ ->
                hasError = true
                isPrepared = false
                isPlaying = false
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            hasError = true
            isPrepared = false
            isPlaying = false
        }
        onDispose {
            runCatching { mp.stop() }
            runCatching { mp.release() }
            mediaPlayerRef = null
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
                .background(Color.Transparent)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    ) {
                        onClose()
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            if (!isPrepared && !hasError) {
                Text(
                    text = stringResource(R.string.loading_text),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            } else if (hasError) {
                Text(
                    text = stringResource(R.string.audio_load_error),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note_2),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(120.dp),
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }

            // Top Title Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
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

            // Bottom Playback Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
