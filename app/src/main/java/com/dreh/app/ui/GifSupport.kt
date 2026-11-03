package com.dreh.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** GIF-aware Coil request for a drawable resource name, downscaled to keep memory sane.
 *  Always decodes as an animated drawable - no static bitmap caching through a shared key,
 *  which made the companion freeze on its first frame. */
@Composable
fun gifRequest(name: String, targetPx: Int): ImageRequest {
    val ctx = LocalContext.current
    val res = remember(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
    return remember(name, res, targetPx) {
        ImageRequest.Builder(ctx)
            .data(res)
            .size(targetPx)
            .crossfade(false)
            .build()
    }
}

/** Animated GIF at Figma coordinates, decoded by Coil (the coil-gif artifact adds the GIF decoder).
 *  [srcSizePx] forces the decode size - pass the SAME size another screen uses so both hit the
 *  one Coil memory-cache entry (the little chat echo shares the big home companion's frame). */
@Composable
fun FGif(
    name: String, x: Float, y: Float, w: Float, h: Float,
    contentScale: ContentScale = ContentScale.Fit,
    srcSizePx: Int? = null,
    contentDescription: String? = null
) {
    val targetPx = srcSizePx ?: with(LocalDensity.current) {
        (w * LocalScale.current).dp.roundToPx().coerceIn(160, 900)
    }
    Box(Modifier.at(x, y, w, h)) {
        AsyncImage(
            model = gifRequest(name, targetPx),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(), contentScale = contentScale
        )
    }
}
