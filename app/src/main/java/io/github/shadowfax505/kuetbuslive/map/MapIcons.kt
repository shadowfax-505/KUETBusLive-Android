package io.github.shadowfax505.kuetbuslive.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

// Map markers drawn in code, so they stay sharp at every screen density.
// The bus is seen from above with its nose pointing up (north): the map rotates
// the image by the bus heading, so it turns like a real vehicle on the road.
object MapIcons {

    fun bus(context: Context, bodyColor: Int, selected: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val bodyW = 24f * d
        val bodyH = 54f * d
        val pad = 9f * d
        val w = (bodyW + pad * 2).toInt()
        val h = (bodyH + pad * 2).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = pad
        val top = pad
        val body = RectF(left, top, left + bodyW, top + bodyH)
        val radius = 7f * d

        // Soft shadow under the bus.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            setShadowLayer(5f * d, 0f, 2.5f * d, Color.argb(90, 0, 0, 0))
        }
        canvas.drawRoundRect(body, radius, radius, shadow)

        // White outline when selected.
        if (selected) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val outer = RectF(body.left - 2.5f * d, body.top - 2.5f * d, body.right + 2.5f * d, body.bottom + 2.5f * d)
            canvas.drawRoundRect(outer, radius + 2.5f * d, radius + 2.5f * d, ring)
        }

        // Side mirrors at the front.
        val mirror = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darken(bodyColor, 0.65f) }
        canvas.drawRoundRect(RectF(body.left - 3f * d, top + 7f * d, body.left + 1f * d, top + 11f * d), d, d, mirror)
        canvas.drawRoundRect(RectF(body.right - 1f * d, top + 7f * d, body.right + 3f * d, top + 11f * d), d, d, mirror)

        // Body with a light sheen from left to right.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                body.left, 0f, body.right, 0f,
                intArrayOf(darken(bodyColor, 0.85f), lighten(bodyColor, 0.18f), darken(bodyColor, 0.85f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(body, radius, radius, paint)

        // Windscreen at the front.
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 30, 58) }
        val windscreen = Path().apply {
            moveTo(body.left + 3f * d, top + 6.5f * d)
            quadTo(body.centerX(), top + 2.5f * d, body.right - 3f * d, top + 6.5f * d)
            lineTo(body.right - 3.5f * d, top + 11.5f * d)
            quadTo(body.centerX(), top + 10f * d, body.left + 3.5f * d, top + 11.5f * d)
            close()
        }
        canvas.drawPath(windscreen, glass)

        // Roof panel with two hatches and an air-con unit.
        val roof = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) }
        val roofRect = RectF(body.left + 4f * d, top + 14f * d, body.right - 4f * d, body.bottom - 7f * d)
        canvas.drawRoundRect(roofRect, 4f * d, 4f * d, roof)
        val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 11, 27, 58) }
        val hatchW = roofRect.width() * 0.55f
        val cx = roofRect.centerX()
        canvas.drawRoundRect(RectF(cx - hatchW / 2, roofRect.top + 5f * d, cx + hatchW / 2, roofRect.top + 11f * d), 1.5f * d, 1.5f * d, hatch)
        canvas.drawRoundRect(RectF(cx - hatchW / 2, roofRect.bottom - 11f * d, cx + hatchW / 2, roofRect.bottom - 5f * d), 1.5f * d, 1.5f * d, hatch)
        val ac = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(35, 11, 27, 58) }
        canvas.drawRoundRect(RectF(cx - hatchW / 2, roofRect.centerY() - 4f * d, cx + hatchW / 2, roofRect.centerY() + 4f * d), 2f * d, 2f * d, ac)

        // Rear window.
        canvas.drawRoundRect(RectF(body.left + 5f * d, body.bottom - 5f * d, body.right - 5f * d, body.bottom - 2.5f * d), d, d, glass)

        // Headlights.
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 214, 102) }
        canvas.drawCircle(body.left + 4f * d, top + 2.6f * d, 1.5f * d, light)
        canvas.drawCircle(body.right - 4f * d, top + 2.6f * d, 1.5f * d, light)
        return bitmap
    }

    // Where the rider gets on: a ring with a solid centre.
    fun boardingPoint(context: Context, color: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (30 * d).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            setShadowLayer(4f * d, 0f, 1.5f * d, Color.argb(90, 0, 0, 0))
        }
        canvas.drawCircle(c, c, 10f * d, shadow)
        canvas.drawCircle(c, c, 7.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.drawCircle(c, c, 3f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        return bitmap
    }

    // A waiting rider, shown on the driver's map.
    fun wave(context: Context): Bitmap = boardingPoint(context, Color.rgb(255, 160, 40))

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt(),
    )

    private fun lighten(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt(),
    )
}
