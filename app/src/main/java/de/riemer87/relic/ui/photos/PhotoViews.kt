package de.riemer87.relic.ui.photos

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.riemer87.relic.R
import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.photos.LocalPhotoLoader
import de.riemer87.relic.photos.PhotoSize
import de.riemer87.relic.ui.theme.Relic
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** Ein Foto eines Tages. Solange es lädt (oder wenn es fehlt), bleibt eine ruhige Fläche stehen. */
@Composable
fun PhotoImage(
    date: LocalDate,
    photo: DayPhoto,
    maxEdgePx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    placeholder: Color = Relic.colors.rule.copy(alpha = 0.35f),
) {
    val loader = LocalPhotoLoader.current
    val image by produceState<ImageBitmap?>(null, date, photo, maxEdgePx) {
        value = loader?.load(date, photo, maxEdgePx)
    }
    val current = image
    if (current != null) {
        Image(current, contentDescription, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(placeholder))
    }
}

/**
 * Vollbild für die Fotos **eines** Tages, mit den üblichen Galerie-Gesten:
 * Wischen blättert (nur innerhalb dieses Tages), zwei Finger oder Doppeltippen zoomen,
 * Ziehen nach unten oder oben schließt die Ansicht. Die Rechenregeln stehen in [ZoomMath].
 */
@Composable
fun PhotoViewer(
    date: LocalDate,
    photos: List<DayPhoto>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) { photos.size }
    val screen = LocalWindowInfo.current.containerSize
    // Gespeicherte Fotos voll laden (höchstens 2560 px), damit sie auch vergrößert scharf bleiben.
    val maxEdge = maxOf(screen.width, screen.height, PhotoSize.MAX_EDGE)
    val scope = rememberCoroutineScope()
    var height by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val fade = ZoomMath.backgroundAlpha(dragY, height)
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { height = it.height.toFloat() }
                .background(Color.Black.copy(alpha = fade)),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomablePhoto(
                    date = date,
                    photo = photos[page],
                    maxEdgePx = maxEdge,
                    contentDescription = stringResource(R.string.photo_number, page + 1),
                    isCurrent = pagerState.settledPage == page,
                    dismissOffsetY = if (pagerState.currentPage == page) dragY else 0f,
                    onDismissDrag = { dragY += it },
                    onDismissEnd = { velocityY ->
                        scope.launch {
                            if (ZoomMath.shouldDismiss(dragY, velocityY, height)) {
                                val target = if (dragY < 0f) -height else height
                                animate(dragY, target, animationSpec = tween(180)) { value, _ -> dragY = value }
                                onDismiss()
                            } else {
                                animate(dragY, 0f, animationSpec = spring()) { value, _ -> dragY = value }
                            }
                        }
                    },
                )
            }
            Box(Modifier.fillMaxSize().safeDrawingPadding().graphicsLayer { alpha = fade }) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.photo_close), tint = Color.White)
                }
                if (photos.size > 1) {
                    Text(
                        text = stringResource(R.string.photo_position, pagerState.currentPage + 1, photos.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

/** Was ein Finger gerade auf dem Foto tut. Entschieden wird erst, wenn er sich ein Stück bewegt hat. */
private enum class Gesture { Undecided, Transform, Dismiss, Pager }

/**
 * Ein Foto im Vollbild, das sich zoomen und verschieben lässt. Bewegungen, die es nicht selbst
 * braucht (Foto ungezoomt oder schon am Rand), lässt es durch – dann blättert die Ansicht weiter.
 */
@Composable
private fun ZoomablePhoto(
    date: LocalDate,
    photo: DayPhoto,
    maxEdgePx: Int,
    contentDescription: String,
    isCurrent: Boolean,
    dismissOffsetY: Float,
    onDismissDrag: (Float) -> Unit,
    onDismissEnd: (velocityY: Float) -> Unit,
) {
    val loader = LocalPhotoLoader.current
    val image by produceState<ImageBitmap?>(null, date, photo, maxEdgePx) {
        value = loader?.load(date, photo, maxEdgePx)
    }
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf(Size.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val imageSize = image?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Zero
    val fitted = ZoomMath.fittedSize(container, imageSize)

    // Wer weiterblättert, findet das vorige Foto beim Zurückkommen wieder in normaler Größe vor.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { container = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(fitted) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val startScale = scale
                        val startOffset = offset
                        val targetScale = if (startScale > 1.01f) 1f else ZoomMath.DOUBLE_TAP_SCALE
                        val targetOffset = ZoomMath.clampOffset(
                            ZoomMath.zoomAround(tap - container.center, startOffset, startScale, targetScale),
                            ZoomMath.maxOffset(container, fitted, targetScale),
                        )
                        scope.launch {
                            animate(0f, 1f, animationSpec = tween(250)) { t, _ ->
                                scale = lerp(startScale, targetScale, t)
                                offset = lerp(startOffset, targetOffset, t)
                            }
                        }
                    },
                )
            }
            .pointerInput(fitted) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val velocity = VelocityTracker()
                    var gesture = Gesture.Undecided
                    var slopPan = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pan = event.calculatePan()
                        if (gesture == Gesture.Undecided) {
                            if (event.changes.count { it.pressed } >= 2) {
                                gesture = Gesture.Transform
                            } else {
                                slopPan += pan
                                if (slopPan.getDistance() > viewConfiguration.touchSlop) {
                                    gesture = when {
                                        scale > 1.01f -> {
                                            val max = ZoomMath.maxOffset(container, fitted, scale)
                                            if (ZoomMath.panMovesImage(offset, slopPan, max)) Gesture.Transform else Gesture.Pager
                                        }
                                        abs(slopPan.y) > abs(slopPan.x) -> Gesture.Dismiss
                                        else -> Gesture.Pager
                                    }
                                }
                            }
                        }
                        when (gesture) {
                            Gesture.Transform -> {
                                val oldScale = scale
                                val newScale = ZoomMath.clampScale(oldScale * event.calculateZoom())
                                val focus = event.calculateCentroid()
                                val zoomed = if (focus.isSpecified) {
                                    ZoomMath.zoomAround(focus - container.center, offset, oldScale, newScale)
                                } else {
                                    offset
                                }
                                scale = newScale
                                offset = ZoomMath.clampOffset(zoomed + pan, ZoomMath.maxOffset(container, fitted, newScale))
                                event.changes.forEach { it.consume() }
                            }
                            Gesture.Dismiss -> {
                                event.changes.firstOrNull { it.pressed }?.let { velocity.addPosition(it.uptimeMillis, it.position) }
                                onDismissDrag(pan.y)
                                event.changes.forEach { it.consume() }
                            }
                            Gesture.Undecided, Gesture.Pager -> Unit
                        }
                    } while (event.changes.any { it.pressed })
                    if (gesture == Gesture.Dismiss) onDismissEnd(velocity.calculateVelocity().y)
                }
            },
    ) {
        val current = image
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + dismissOffsetY
                },
            )
        } else {
            // Solange es lädt (oder wenn es fehlt): ruhige schwarze Fläche, für Bedienhilfen trotzdem benannt.
            Box(Modifier.fillMaxSize().semantics { this.contentDescription = contentDescription })
        }
    }
}
