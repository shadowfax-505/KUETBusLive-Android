package io.github.shadowfax505.kuetbuslive.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.ContextCompat
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.data.BusStyle
import io.github.shadowfax505.kuetbuslive.data.BusStyles
import io.github.shadowfax505.kuetbuslive.map.MapIcons

// The pictures on the widget: the trip track, the bus riding on it, and the ring.
// A widget can't draw by itself, so these are bitmaps. The track and ring are drawn
// in white with see-through parts; the layout tints them with the widget's text
// colour, so they turn white at night along with the text. The bus keeps its colours.
object WidgetArt {

    // Height of the track picture. The bus is drawn the same height, on top of it.
    const val TRACK_HEIGHT_DP = 30f

    // The road from KUET to the rider's stop: a faint line, the part driven so far in
    // full colour, a dot at each stop on the way and a ring at the rider's stop.
    fun track(context: Context, widthDp: Float, progress: Double, stopFractions: List<Double>): Bitmap {
        val d = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((widthDp * d).toInt().coerceAtLeast(1), (TRACK_HEIGHT_DP * d).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val geo = Geometry(bitmap.width.toFloat(), d, progress, BusStyles.current(context))
        val line = 5f * d
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        paint.alpha = 40
        canvas.drawRoundRect(RectF(geo.left, geo.roadY - line / 2, geo.end, geo.roadY + line / 2), line / 2, line / 2, paint)
        paint.alpha = 255
        canvas.drawRoundRect(RectF(geo.left, geo.roadY - line / 2, geo.busX, geo.roadY + line / 2), line / 2, line / 2, paint)

        // Stops on the way: filled once passed, faint ahead.
        stopFractions.filter { it > 0.02 && it < 0.98 }.forEach { f ->
            paint.alpha = if (f <= progress) 255 else 90
            canvas.drawCircle(geo.start + (geo.end - geo.start) * f.toFloat(), geo.roadY, 3.5f * d, paint)
        }

        // The rider's stop: a ring with a hole that shows the widget behind it.
        paint.alpha = 255
        val ring = 7f * d
        canvas.drawCircle(geo.end, geo.roadY, ring, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawCircle(geo.end, geo.roadY, ring - 3.5f * d, paint)
        return bitmap
    }

    // The bus alone, at its place on the track, nose to the right. Same size as track().
    fun bus(context: Context, widthDp: Float, progress: Double, teacher: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((widthDp * d).toInt().coerceAtLeast(1), (TRACK_HEIGHT_DP * d).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val style = BusStyles.current(context)
        val geo = Geometry(bitmap.width.toFloat(), d, progress, style)
        val busW = BUS_WIDTH_DP * d
        when (style) {
            BusStyle.TOP -> {
                // The map icon points north and carries a shadow margin; turn it to face right.
                val icon = MapIcons.bus(context, teacher = teacher, selected = false, dark = false)
                val turned = Bitmap.createBitmap(icon, 0, 0, icon.width, icon.height, Matrix().apply { postRotate(90f) }, true)
                val busH = busW * turned.height / turned.width
                val dst = RectF(geo.busX - busW / 2, geo.roadY - busH / 2, geo.busX + busW / 2, geo.roadY + busH / 2)
                canvas.drawBitmap(turned, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            BusStyle.SIDE -> {
                val drawable = ContextCompat.getDrawable(context, R.drawable.bus_side) ?: return bitmap
                val busH = busW * drawable.intrinsicHeight / drawable.intrinsicWidth
                // The road runs under the wheels, near the bottom of the picture.
                val top = geo.roadY - busH * 0.86f
                drawable.setBounds((geo.busX - busW / 2).toInt(), top.toInt(), (geo.busX + busW / 2).toInt(), (top + busH).toInt())
                drawable.draw(canvas)
            }
        }
        return bitmap
    }

    // A ring that fills clockwise from the top as the trip goes on.
    fun ring(context: Context, sizeDp: Float, fraction: Double, lineDp: Float): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (sizeDp * d).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val line = lineDp * d
        val oval = RectF(line / 2, line / 2, size - line / 2, size - line / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = line
            strokeCap = Paint.Cap.ROUND
        }
        paint.alpha = 40
        canvas.drawOval(oval, paint)
        val sweep = 360f * fraction.coerceIn(0.0, 1.0).toFloat()
        if (sweep > 0.5f) {
            paint.alpha = 255
            canvas.drawArc(oval, -90f, sweep, false, paint)
        }
        return bitmap
    }

    private const val BUS_WIDTH_DP = 44f

    // Where things sit on the track, shared by track() and bus() so they line up.
    private class Geometry(width: Float, d: Float, progress: Double, style: BusStyle) {
        val left = 2f * d
        val start = BUS_WIDTH_DP * d * 0.5f
        val end = width - 8f * d
        val roadY = TRACK_HEIGHT_DP * d * (if (style == BusStyle.SIDE) 0.82f else 0.5f)
        val busX = start + (end - start) * progress.coerceIn(0.0, 1.0).toFloat()
    }
}
