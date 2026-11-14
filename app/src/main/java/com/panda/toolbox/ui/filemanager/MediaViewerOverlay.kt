package com.panda.toolbox.ui.filemanager

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_IMAGE_DIMENSION = 2048
private const val SEEK_STEP_MS = 10_000
private const val OVERLAY_AUTO_HIDE_MS = 3_000L

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImageViewerOverlay(
    item: FileItem,
    onClose: () -> Unit
) {
    var imageBitmap by remember(item.path) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(item.path) { mutableStateOf(true) }
    var loadFailed by remember(item.path) { mutableStateOf(false) }
    var showOverlay by remember(item.path) { mutableStateOf(true) }
    var interactionTick by remember(item.path) { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(item.path) {
        isLoading = true
        loadFailed = false
        imageBitmap = null
        val decoded = withContext(Dispatchers.IO) {
            decodeImageBitmap(item.file)
        }
        imageBitmap = decoded
        loadFailed = decoded == null
        isLoading = false
    }

    LaunchedEffect(showOverlay, interactionTick) {
        if (showOverlay) {
            delay(OVERLAY_AUTO_HIDE_MS)
            showOverlay = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
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
        when {
            isLoading -> {
                Text(
                    text = stringResource(R.string.loading_text),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            loadFailed || imageBitmap == null -> {
                Text(
                    text = stringResource(R.string.image_load_error),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }
        }
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoViewerOverlay(
    item: FileItem,
    onClose: () -> Unit
) {
    var videoViewRef by remember(item.path) { mutableStateOf<VideoView?>(null) }
    var isPrepared by remember(item.path) { mutableStateOf(false) }
    var isPlaying by remember(item.path) { mutableStateOf(false) }
    var hasError by remember(item.path) { mutableStateOf(false) }
    var durationMs by remember(item.path) { mutableIntStateOf(0) }
    var positionMs by remember(item.path) { mutableIntStateOf(0) }
    var showOverlay by remember(item.path) { mutableStateOf(true) }
    var interactionTick by remember(item.path) { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

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
            val view = videoViewRef
            if (view != null) {
                positionMs = view.currentPosition.coerceAtLeast(0)
            }
            delay(500L)
        }
    }

    DisposableEffect(item.path) {
        onDispose {
            videoViewRef?.stopPlayback()
            videoViewRef = null
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
                            val view = videoViewRef
                            if (view != null && isPrepared) {
                                if (view.isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    view.start()
                                    isPlaying = true
                                }
                            }
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
            factory = { context ->
                VideoView(context).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    videoViewRef = this
                    setOnPreparedListener { mp ->
                        isPrepared = true
                        hasError = false
                        durationMs = mp.duration.coerceAtLeast(0)
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        positionMs = durationMs
                    }
                    setOnErrorListener { _, _, _ ->
                        hasError = true
                        isPrepared = false
                        isPlaying = false
                        true
                    }
                    setVideoPath(item.path)
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
                    .background(Color.Black.copy(alpha = 0.72f))
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
                    onClick = {
                        val view = videoViewRef ?: return@OutlinedButton
                        if (!isPrepared) return@OutlinedButton
                        val target = (view.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)
                        view.seekTo(target)
                        positionMs = target
                    },
                    enabled = isPrepared
                ) {
                    Text(text = stringResource(R.string.rewind_10s))
                }

                Button(
                    onClick = {
                        val view = videoViewRef ?: return@Button
                        if (!isPrepared) return@Button
                        if (view.isPlaying) {
                            view.pause()
                            isPlaying = false
                        } else {
                            view.start()
                            isPlaying = true
                        }
                    },
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
                    onClick = {
                        val view = videoViewRef ?: return@OutlinedButton
                        if (!isPrepared) return@OutlinedButton
                        val maxPos = if (durationMs > 0) durationMs else view.duration.coerceAtLeast(0)
                        val target = (view.currentPosition + SEEK_STEP_MS).coerceAtMost(maxPos)
                        view.seekTo(target)
                        positionMs = target
                    },
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

private fun decodeImageBitmap(file: File): ImageBitmap? {
    return try {
        val source = ImageDecoder.createSource(file)
        val bitmap: Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val maxDim = max(width, height)
            if (maxDim > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
        bitmap.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

private fun formatDuration(ms: Int): String {
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
