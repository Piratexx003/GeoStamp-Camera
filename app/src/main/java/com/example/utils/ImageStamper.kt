package com.example.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageStamper {

    fun stampImage(
        bitmap: Bitmap,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        address: String,
        stampColorHex: String = "#FFFFFF",
        stampStyle: String = "Modern"
    ): Bitmap {
        // Create a mutable copy of the bitmap to draw on
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        // Scaled layout parameters relative to width (w) to ensure proportional rendering
        val baseSize = w.coerceAtMost(h)
        val titleTextSize = baseSize * 0.026f
        val bodyTextSize = baseSize * 0.022f
        val spacing = baseSize * 0.015f
        val padding = baseSize * 0.035f

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(stampColorHex)
            textSize = bodyTextSize
            style = Paint.Style.FILL
        }

        val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(stampColorHex)
            textSize = titleTextSize
            isFakeBoldText = true
            style = Paint.Style.FILL
        }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm:ss (z)", Locale.getDefault())
        val dateString = sdfDate.format(Date(timestamp))
        val timeString = sdfTime.format(Date(timestamp))

        val latStr = String.format(Locale.US, "LATITUDE: %.6f°", latitude)
        val lonStr = String.format(Locale.US, "LONGITUDE: %.6f°", longitude)
        val altStr = String.format(Locale.US, "ALTITUDE: %.1fm", altitude)
        val brandStr = "GEOSTAMP CAM v1.0"

        val lines = mutableListOf<String>()
        lines.add("$dateString  $timeString")
        lines.add("$latStr    $lonStr")
        if (altitude != 0.0) {
            lines.add(altStr)
        }
        if (address.isNotEmpty() && address != "Retrieving location...") {
            lines.add(address)
        }

        when (stampStyle) {
            "Classic" -> {
                // Retro classic LCD orange/amber camera style, bottom right
                paintText.color = Color.parseColor("#FF8C00") // Classic film orange
                paintText.textSize = titleTextSize
                paintText.isFakeBoldText = true

                var currentY = h - padding
                // Retro style: bottom to top or top to bottom. Let's draw bottom to top
                lines.reversed().forEachIndexed { index, line ->
                    val x = w - padding - paintText.measureText(line)
                    // Draw a subtle shadow for high contrast on light backgrounds
                    paintText.color = Color.BLACK
                    canvas.drawText(line, x + 2, currentY + 2, paintText)

                    paintText.color = Color.parseColor("#FF8C00")
                    canvas.drawText(line, x, currentY, paintText)
                    currentY -= (paintText.textSize + spacing)
                }
            }
            "Minimalist" -> {
                // Minimalist look: Bottom light overlay (no backdrop box, text with clean offset shadow)
                var currentY = h - padding - (lines.size - 1) * (bodyTextSize + spacing)
                lines.forEach { line ->
                    val x = padding
                    // Shadow
                    paintText.color = Color.parseColor("#40000000")
                    canvas.drawText(line, x + 2, currentY + 2, paintText)

                    paintText.color = Color.parseColor(stampColorHex)
                    canvas.drawText(line, x, currentY, paintText)
                    currentY += (bodyTextSize + spacing)
                }
            }
            else -> {
                // Modern style (Default): Sleek semi-transparent slate card container in bottom-left/right
                val maxLineWidth = lines.maxOfOrNull { paintText.measureText(it) } ?: 0f
                val cardWidth = maxOf(maxLineWidth, paintTitle.measureText(brandStr)) + padding * 2
                val cardHeight = lines.size * (bodyTextSize + spacing) + titleTextSize + padding * 2.5f

                // Dimensions of card
                val left = padding
                val bottom = h - padding
                val right = left + cardWidth
                val top = bottom - cardHeight

                val paintCard = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#A6111827") // Deep dark-gray / slate 900 background with 65% opacity
                    style = Paint.Style.FILL
                }

                val paintCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#4DFFFFFF") // Thin white borders
                    strokeWidth = baseSize * 0.002f
                    style = Paint.Style.STROKE
                }

                // Draw card rounded rectangle
                val rect = RectF(left, top, right, bottom)
                canvas.drawRoundRect(rect, baseSize * 0.015f, baseSize * 0.015f, paintCard)
                canvas.drawRoundRect(rect, baseSize * 0.015f, baseSize * 0.015f, paintCardBorder)

                // Draw Brand Title
                paintTitle.color = Color.parseColor(stampColorHex)
                var currentY = top + padding + titleTextSize
                canvas.drawText(brandStr, left + padding, currentY, paintTitle)

                // Divider line
                val dividerY = currentY + spacing
                val paintDivider = Paint().apply {
                    color = Color.parseColor("#4DFFFFFF")
                    strokeWidth = baseSize * 0.0015f
                }
                canvas.drawLine(left + padding, dividerY, right - padding, dividerY, paintDivider)

                // Draw Lines of Meta Address, Coordinates, Date
                currentY = dividerY + spacing + bodyTextSize
                lines.forEach { line ->
                    paintText.color = Color.parseColor(stampColorHex)
                    canvas.drawText(line, left + padding, currentY, paintText)
                    currentY += (bodyTextSize + spacing)
                }
            }
        }

        return resultBitmap
    }
}
