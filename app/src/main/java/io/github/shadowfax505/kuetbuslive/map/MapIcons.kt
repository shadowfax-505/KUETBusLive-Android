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
import android.graphics.Typeface

// Map markers drawn in code, so they stay sharp at every screen density.
// The bus is seen from above with its nose pointing up (north): the map rotates
// the image by the bus heading, so it turns like a real vehicle on the road.
object MapIcons {

    // A glossy white bus. Teacher buses carry a dark roof panel so the two are easy to tell apart.
    fun bus(context: Context, teacher: Boolean, selected: Boolean, dark: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val bodyW = 22f * d
        val bodyH = 52f * d
        val pad = 9f * d
        val w = (bodyW + pad * 2).toInt()
        val h = (bodyH + pad * 2).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val top = pad
        val body = RectF(pad, top, pad + bodyW, top + bodyH)
        val radius = 6.5f * d

        // Soft shadow under the bus.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            setShadowLayer(5f * d, 0f, 2.5f * d, Color.argb(110, 0, 0, 0))
        }
        canvas.drawRoundRect(body, radius, radius, shadow)

        // Outline when selected: black on a light map, white on a dark one.
        if (selected) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) Color.WHITE else Color.BLACK }
            val g = 2.5f * d
            canvas.drawRoundRect(RectF(body.left - g, body.top - g, body.right + g, body.bottom + g), radius + g, radius + g, ring)
        }

        // Side mirrors at the front.
        val mirror = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40) }
        canvas.drawRoundRect(RectF(body.left - 3f * d, top + 7f * d, body.left + 1f * d, top + 10.5f * d), d, d, mirror)
        canvas.drawRoundRect(RectF(body.right - 1f * d, top + 7f * d, body.right + 3f * d, top + 10.5f * d), d, d, mirror)

        // White body with a glossy sheen across it.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                body.left, 0f, body.right, 0f,
                intArrayOf(Color.rgb(214, 214, 214), Color.WHITE, Color.rgb(246, 246, 246), Color.rgb(208, 208, 208)),
                floatArrayOf(0f, 0.38f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(body, radius, radius, paint)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * d
            color = Color.argb(70, 0, 0, 0)
        }
        canvas.drawRoundRect(body, radius, radius, edge)

        // Dark tinted windscreen at the front.
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, top, 0f, top + 12f * d, Color.rgb(70, 70, 74), Color.rgb(18, 18, 20), Shader.TileMode.CLAMP)
        }
        val windscreen = Path().apply {
            moveTo(body.left + 2.5f * d, top + 6f * d)
            quadTo(body.centerX(), top + 2.2f * d, body.right - 2.5f * d, top + 6f * d)
            lineTo(body.right - 3f * d, top + 11.5f * d)
            quadTo(body.centerX(), top + 10f * d, body.left + 3f * d, top + 11.5f * d)
            close()
        }
        canvas.drawPath(windscreen, glass)

        // Side windows as thin dark strips.
        val side = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 30, 30, 32) }
        canvas.drawRoundRect(RectF(body.left + 1.2f * d, top + 14f * d, body.left + 2.6f * d, body.bottom - 8f * d), d, d, side)
        canvas.drawRoundRect(RectF(body.right - 2.6f * d, top + 14f * d, body.right - 1.2f * d, body.bottom - 8f * d), d, d, side)

        // Roof panel with two hatches and an air-con unit.
        val roofRect = RectF(body.left + 4f * d, top + 14f * d, body.right - 4f * d, body.bottom - 7f * d)
        val roof = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (teacher) Color.rgb(48, 48, 50) else Color.rgb(236, 236, 236)
        }
        canvas.drawRoundRect(roofRect, 3.5f * d, 3.5f * d, roof)
        val detail = if (teacher) Color.argb(90, 255, 255, 255) else Color.argb(45, 0, 0, 0)
        val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = detail }
        val hatchW = roofRect.width() * 0.6f
        val cx = roofRect.centerX()
        canvas.drawRoundRect(RectF(cx - hatchW / 2, roofRect.top + 4.5f * d, cx + hatchW / 2, roofRect.top + 10f * d), 1.5f * d, 1.5f * d, hatch)
        canvas.drawRoundRect(RectF(cx - hatchW / 2, roofRect.bottom - 10f * d, cx + hatchW / 2, roofRect.bottom - 4.5f * d), 1.5f * d, 1.5f * d, hatch)
        canvas.drawRoundRect(RectF(cx - hatchW / 2, roofRect.centerY() - 3.5f * d, cx + hatchW / 2, roofRect.centerY() + 3.5f * d), 2f * d, 2f * d, hatch)

        // Rear window and tail lights.
        val rear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 28, 30) }
        canvas.drawRoundRect(RectF(body.left + 5f * d, body.bottom - 5f * d, body.right - 5f * d, body.bottom - 2.6f * d), d, d, rear)
        val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 48, 49) }
        canvas.drawRoundRect(RectF(body.left + 1.5f * d, body.bottom - 3f * d, body.left + 4f * d, body.bottom - 1.2f * d), 0.8f * d, 0.8f * d, tail)
        canvas.drawRoundRect(RectF(body.right - 4f * d, body.bottom - 3f * d, body.right - 1.5f * d, body.bottom - 1.2f * d), 0.8f * d, 0.8f * d, tail)

        // Headlights.
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 250, 240) }
        val lightEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f * d; color = Color.argb(90, 0, 0, 0) }
        listOf(body.left + 4f * d, body.right - 4f * d).forEach { x ->
            canvas.drawCircle(x, top + 2.4f * d, 1.4f * d, light)
            canvas.drawCircle(x, top + 2.4f * d, 1.4f * d, lightEdge)
        }
        return bitmap
    }

    // Where the rider gets on: a black ring with a white centre (inverted on a dark map).
    fun boardingPoint(context: Context, dark: Boolean): Bitmap =
        ring(context, if (dark) Color.WHITE else Color.BLACK, if (dark) Color.BLACK else Color.WHITE)

    // A waiting rider, shown on the driver's map: a grey ring so it doesn't compete with the route.
    fun wave(context: Context, dark: Boolean): Bitmap =
        ring(context, if (dark) Color.rgb(200, 200, 200) else Color.rgb(90, 90, 90), Color.WHITE)

    // A small rounded label such as "4 min", drawn here so it needs no map fonts.
    fun pill(context: Context, text: String, dark: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.BLACK else Color.WHITE
            textSize = 13f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textW = textPaint.measureText(text)
        val padX = 10f * d
        val pillH = 26f * d
        val tail = 6f * d
        val shadowPad = 4f * d
        val w = (textW + padX * 2 + shadowPad * 2).toInt()
        val h = (pillH + tail + shadowPad * 2).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(shadowPad, shadowPad, w - shadowPad, shadowPad + pillH)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.WHITE else Color.BLACK
            setShadowLayer(3f * d, 0f, 1.5f * d, Color.argb(80, 0, 0, 0))
        }
        val shape = Path().apply {
            addRoundRect(rect, pillH / 2, pillH / 2, Path.Direction.CW)
            moveTo(rect.centerX() - tail, rect.bottom - 1f)
            lineTo(rect.centerX(), rect.bottom + tail)
            lineTo(rect.centerX() + tail, rect.bottom - 1f)
            close()
        }
        canvas.drawPath(shape, bg)
        val fm = textPaint.fontMetrics
        canvas.drawText(text, rect.left + padX, rect.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
        return bitmap
    }

    private fun ring(context: Context, outer: Int, inner: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (30 * d).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outer
            setShadowLayer(4f * d, 0f, 1.5f * d, Color.argb(90, 0, 0, 0))
        }
        canvas.drawCircle(c, c, 9f * d, shadow)
        canvas.drawCircle(c, c, 3.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inner })
        return bitmap
    }
}
