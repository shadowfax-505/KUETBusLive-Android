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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.data.BusStyle
import io.github.shadowfax505.kuetbuslive.data.BusStyles
import io.github.shadowfax505.kuetbuslive.map.MapIcons

// The trip's progress as a road: a thin track, the part already driven filled in,
// the rider's stop as a ring at the end, and a glossy bus riding along it, nose first.
// The rider picks in Profile whether the bus is seen from above (as on the map) or from the side.
@Composable
fun BusProgressBar(progress: Float, teacher: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val style = BusStyles.current(context)
    val busWidth = 52.dp
    val painter = busPainter(style, teacher)
    val busHeight = busWidth * painter.intrinsicSize.height / painter.intrinsicSize.width
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.onSurface

    // Seen from above the road runs under the middle of the bus; from the side, under its wheels.
    val roadY = busHeight * (if (style == BusStyle.SIDE) 0.86f else 0.5f)

    BoxWithConstraints(modifier.fillMaxWidth().height(busHeight)) {
        val p = progress.coerceIn(0f, 1f)
        val stopSize = 14.dp
        // The bus centre travels from its own half-length in to the stop ring.
        val travel = maxWidth - busWidth * 0.6f - stopSize / 2
        val centre = busWidth * 0.3f + travel * p

        Box(Modifier.offset(y = roadY - 3.dp).fillMaxWidth().height(6.dp).background(track, RoundedCornerShape(3.dp)))
        Box(Modifier.offset(y = roadY - 3.dp).width(centre).height(6.dp).background(fill, RoundedCornerShape(3.dp)))
        Box(
            Modifier.align(Alignment.TopEnd).offset(y = roadY - stopSize / 2).size(stopSize)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(4.dp, fill, CircleShape),
        )
        Image(
            painter,
            contentDescription = null,
            modifier = Modifier.offset(x = centre - busWidth / 2).size(busWidth, busHeight),
        )
    }
}

@Composable
private fun busPainter(style: BusStyle, teacher: Boolean): Painter {
    val context = LocalContext.current
    return when (style) {
        BusStyle.TOP -> remember(teacher) {
            BitmapPainter(sideways(MapIcons.bus(context, teacher = teacher, selected = false, dark = false)).asImageBitmap())
        }
        BusStyle.SIDE -> painterResource(R.drawable.bus_side)
    }
}

// The map icon points north; on the bar it drives to the right.
private fun sideways(bitmap: Bitmap): Bitmap =
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(90f) }, true)

// One style's bus on its own, for the picker in Profile.
@Composable
fun BusPreview(style: BusStyle, modifier: Modifier = Modifier) {
    val painter = busPainter(style, teacher = false)
    Image(painter, contentDescription = null, modifier = modifier.size(80.dp, 80.dp * painter.intrinsicSize.height / painter.intrinsicSize.width))
}
