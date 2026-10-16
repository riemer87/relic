package de.riemer87.relic.ui.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.riemer87.relic.R
import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.photos.LocalPhotoLoader
import de.riemer87.relic.ui.theme.Relic
import java.time.LocalDate

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
 * Vollbild für die Fotos **eines** Tages. Wischen blättert nur innerhalb dieses Tages.
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
    val maxEdge = maxOf(screen.width, screen.height).coerceAtLeast(1080)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                PhotoImage(
                    date = date,
                    photo = photos[page],
                    maxEdgePx = maxEdge,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    contentDescription = stringResource(R.string.photo_number, page + 1),
                    placeholder = Color.Black,
                )
            }
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
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
