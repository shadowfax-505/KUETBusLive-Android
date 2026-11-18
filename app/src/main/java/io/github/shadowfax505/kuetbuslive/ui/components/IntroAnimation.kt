package io.github.shadowfax505.kuetbuslive.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shadowfax505.kuetbuslive.R
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// The short brand intro played once when the app opens, over the first screen.
// The bus drives in along a dotted road, the location pin drops onto it, KUET
// lights up on the sign and the name fades in; then the logo zooms towards the
// rider and fades away, uncovering the app. About 1.5 seconds in all.
// With animations turned off in the phone's settings it simply fades.

private const val INTRO_MS = 1550f
private const val REDUCED_MS = 1200f

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun outCubic(x: Float) = 1 - (1 - x).pow(3)
private fun outQuint(x: Float) = 1 - (1 - x).pow(5)
private fun inCubic(x: Float) = x * x * x
private fun outBack(x: Float): Float {
    val c = 1.7f
    return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2)
}

@Composable
fun IntroAnimation(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val total = if (reduced) REDUCED_MS else INTRO_MS
        val start = withFrameMillis { it }
        while (t < total) {
            t = (withFrameMillis { it } - start).toFloat()
        }
        onFinished()
    }

    // Everything below is drawn in the 108-unit space of the app icon.
    val logoSize = 300.dp
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (reduced) {
                    alpha = 1 - seg(t, 800f, 1200f)
                } else {
                    val zoom = inCubic(seg(t, 1150f, 1550f))
                    scaleX = 1 + zoom * 7
                    scaleY = 1 + zoom * 7
                    alpha = 1 - outCubic(seg(t, 1250f, 1550f))
                }
            }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(logoSize)) {
                val drive = if (reduced) 1f else outQuint(seg(t, 0f, 620f))
                // Dotted road the bus arrives on.
                Canvas(
                    Modifier
                        .size(logoSize)
                        .graphicsLayer {
                            translationX = (1 - drive) * 40 * size.width / 108
                            alpha = if (reduced) 0f else 1 - seg(t, 700f, 1000f)
                        },
                ) {
                    val unit = size.width / 108
                    var x = -40f
                    while (x <= 150f) {
                        drawCircle(Color.White.copy(alpha = 0.35f), radius = 1.1f * unit, center = Offset(x * unit, 86 * unit))
                        x += 5f
                    }
                }
                Image(
                    painterResource(R.drawable.intro_bus), null,
                    Modifier
                        .size(logoSize)
                        .graphicsLayer {
                            val unit = size.width / 108
                            val bounce = if (reduced) 0f else sin(seg(t, 520f, 760f) * PI.toFloat()) * -0.8f
                            translationX = (1 - drive) * -90 * unit
                            translationY = bounce * unit
                            alpha = if (reduced) 1f else seg(t, 0f, 120f)
                        },
                )
                Image(
                    painterResource(R.drawable.intro_kuet), null,
                    Modifier
                        .size(logoSize)
                        .graphicsLayer {
                            val unit = size.width / 108
                            translationX = (1 - drive) * -90 * unit
                            alpha = if (reduced) 1f else seg(t, 600f, 820f)
                        },
                )
                Image(
                    painterResource(R.drawable.intro_pin), null,
                    Modifier
                        .size(logoSize)
                        .graphicsLayer {
                            val unit = size.width / 108
                            val drop = if (reduced) 1f else seg(t, 450f, 800f)
                            val scale = 0.2f + 0.8f * outBack(drop)
                            transformOrigin = TransformOrigin(72f / 108, 43f / 108)
                            scaleX = scale
                            scaleY = scale
                            translationY = (1 - outCubic(drop)) * -14 * unit
                            alpha = if (reduced) 1f else outCubic(seg(t, 450f, 560f))
                        },
                )
            }
            Text(
                "KUET Bus Live",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .offset(y = (-48).dp)
                    .graphicsLayer {
                        val rise = if (reduced) 1f else seg(t, 650f, 900f)
                        alpha = rise
                        translationY = (1 - outCubic(rise)) * 10.dp.toPx()
                    },
            )
        }
    }
}
