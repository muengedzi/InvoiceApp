package com.invoiceapp.utils

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream

object TemplatePreviewGenerator {

    fun generatePreview(
        context: Context,
        templateId: String,
        templateName: String,
        primaryColor: String,
        secondaryColor: String,
        width: Int = 200,
        height: Int = 280
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background - White card with shadow
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw card background
        paint.color = Color.WHITE
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(cardRect, 16f, 16f, paint)

        // Draw colored header
        paint.color = Color.parseColor(primaryColor)
        val headerRect = RectF(0f, 0f, width.toFloat(), (height * 0.35f).toFloat())
        canvas.drawRoundRect(headerRect, 16f, 16f, paint)

        // Draw header text (INVOICE)
        paint.color = Color.WHITE
        paint.textSize = (width * 0.1f).coerceAtLeast(16f)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("INVOICE", width / 2f, (height * 0.15f).toFloat(), paint)

        // Draw template name
        paint.textSize = (width * 0.08f).coerceAtLeast(12f)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(templateName, width / 2f, (height * 0.27f).toFloat(), paint)

        // Draw accent line
        paint.color = Color.parseColor(secondaryColor)
        paint.strokeWidth = 4f
        canvas.drawLine(width * 0.2f, height * 0.35f, width * 0.8f, height * 0.35f, paint)

        // Draw sample content lines
        paint.color = Color.LTGRAY
        paint.strokeWidth = 2f

        // Bill to section
        canvas.drawRect(width * 0.1f, height * 0.42f, width * 0.4f, height * 0.45f, paint)
        canvas.drawRect(width * 0.1f, height * 0.48f, width * 0.35f, height * 0.5f, paint)
        canvas.drawRect(width * 0.1f, height * 0.53f, width * 0.3f, height * 0.55f, paint)

        // Invoice details section
        canvas.drawRect(width * 0.55f, height * 0.42f, width * 0.85f, height * 0.45f, paint)
        canvas.drawRect(width * 0.55f, height * 0.48f, width * 0.8f, height * 0.5f, paint)
        canvas.drawRect(width * 0.55f, height * 0.53f, width * 0.75f, height * 0.55f, paint)

        // Table header
        paint.color = Color.parseColor(primaryColor)
        paint.alpha = 50
        canvas.drawRect(width * 0.1f, height * 0.6f, width * 0.9f, height * 0.65f, paint)

        // Table rows
        paint.color = Color.LTGRAY
        for (i in 0..2) {
            val y = height * (0.68f + i * 0.08f)
            canvas.drawRect(width * 0.1f, y, width * 0.9f, y + (height * 0.03f), paint)
        }

        // Total line
        paint.color = Color.parseColor(secondaryColor)
        paint.alpha = 80
        canvas.drawRect(width * 0.6f, height * 0.85f, width * 0.9f, height * 0.9f, paint)

        return bitmap
    }

    fun savePreviewToCache(context: Context, templateId: String, bitmap: Bitmap): String {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, "template_preview_$templateId.png")
        val outStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        outStream.close()
        return file.absolutePath
    }

    fun getCachedPreview(context: Context, templateId: String): Bitmap? {
        val file = File(context.cacheDir, "template_preview_$templateId.png")
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }
}