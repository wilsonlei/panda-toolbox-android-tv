package com.panda.toolbox.ui.filemanager

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.panda.toolbox.R
import com.panda.toolbox.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_IMAGE_DIMENSION = 2048

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
