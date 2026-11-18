package io.github.shadowfax505.kuetbuslive.rider

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.map.MapIcons

// The trip's progress as a road: a thin track, the part already driven filled in,
// the rider's stop as a ring at the end, and the same glossy bus as on the map
// riding along it, nose first.
@Composable
fun BusProgressBar(progress: Float, teacher: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bus = remember(teacher) { sideways(MapIcons.bus(context, teacher = teacher, selected = false, dark = false)) }
    val busWidth = 52.dp
    val busHeight = busWidth * bus.height / bus.width
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(modifier.fillMaxWidth().height(busHeight)) {
        val p = progress.coerceIn(0f, 1f)
        val stopSize = 14.dp
        // The bus centre travels from its own half-length in to the stop ring.
        val travel = maxWidth - busWidth * 0.6f - stopSize / 2
        val centre = busWidth * 0.3f + travel * p

        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).background(track, RoundedCornerShape(3.dp)))
        Box(Modifier.align(Alignment.CenterStart).width(centre).height(6.dp).background(fill, RoundedCornerShape(3.dp)))
        Box(
            Modifier.align(Alignment.CenterEnd).size(stopSize)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(4.dp, fill, CircleShape),
        )
        Image(
            bus.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = centre - busWidth / 2).size(busWidth, busHeight),
        )
    }
}

// The map icon points north; on the bar it drives to the right.
private fun sideways(bitmap: Bitmap): Bitmap =
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(90f) }, true)
