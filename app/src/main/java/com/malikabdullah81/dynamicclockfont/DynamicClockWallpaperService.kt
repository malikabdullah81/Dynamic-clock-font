package com.malikabdullah81.dynamicclockfont

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DynamicClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return DynamicClockEngine()
    }

    inner class DynamicClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false

        // Frame-driven animation tracking
        private var animStartTime = 0L
        private val animDuration = 600L // 600ms smooth bloom duration

        private val clockPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val glassPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private val glassBorderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("EEE, MMMM d", Locale.getDefault())

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (visible) {
                    drawFrame()
                    
                    // Check if the bloom animation is still running
                    val elapsed = System.currentTimeMillis() - animStartTime
                    if (elapsed < animDuration) {
                        // Render at ~60fps (16ms) during the animation
                        handler.postDelayed(this, 16L)
                    } else {
                        // Slow down refresh to once per second once animation completes (saves battery)
                        handler.postDelayed(this, 1000L)
                    }
                }
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                // Restart animation progress every single time the screen turns on / lock screen opens
                animStartTime = System.currentTimeMillis()
                handler.removeCallbacks(drawRunnable)
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawClockContent(canvas)
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun drawClockContent(canvas: Canvas) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            val centerX = width / 2f
            val centerY = height * 0.32f

            // Calculate precise animation progress (0.0 to 1.0) with ease-out curve
            val elapsed = System.currentTimeMillis() - animStartTime
            val rawProgress = (elapsed.toFloat() / animDuration).coerceIn(0f, 1f)
            // Cubic ease-out for buttery smooth deceleration
            val animProgress = 1f - Math.pow((1f - rawProgress).toDouble(), 3.0).toFloat()

            canvas.drawColor(Color.BLACK)
            canvas.save()

            // Screen-on bloom scaling effect (blooms from tiny dot to 100%)
            val scale = 0.05f + (0.95f * animProgress)
            canvas.scale(scale, scale, centerX, centerY)

            val clockColor = Color.WHITE
            val baseAlpha = 230
            val currentAlpha = (baseAlpha * animProgress).toInt()
            val strokeWidth = 16f
            val glassEnabled = true
            val glassAlpha = (40 * animProgress).toInt()

            clockPaint.color = clockColor
            clockPaint.alpha = currentAlpha
            clockPaint.strokeWidth = strokeWidth

            // Glassmorphism card container
            if (glassEnabled) {
                val cardWidth = 660f
                val cardHeight = 280f
                val rect = RectF(
                    centerX - cardWidth / 2f,
                    centerY - cardHeight / 2f - 10f,
                    centerX + cardWidth / 2f,
                    centerY + cardHeight / 2f + 10f
                )

                glassPaint.color = Color.argb(glassAlpha, 255, 255, 255)
                glassBorderPaint.color = Color.argb((80 * animProgress).toInt(), 255, 255, 255)

                canvas.drawRoundRect(rect, 56f, 56f, glassPaint)
                canvas.drawRoundRect(rect, 56f, 56f, glassBorderPaint)
            }

            // Time digits (One UI outline style)
            val currentTime = timeFormat.format(Date())
            clockPaint.textSize = 190f
            clockPaint.style = Paint.Style.STROKE
            val timeWidth = clockPaint.measureText(currentTime)
            canvas.drawText(currentTime, centerX - timeWidth / 2f, centerY + 20f, clockPaint)

            // Date subtitle
            val currentDate = dateFormat.format(Date())
            clockPaint.textSize = 42f
            clockPaint.style = Paint.Style.FILL
            clockPaint.alpha = (200 * animProgress).toInt()
            val dateWidth = clockPaint.measureText(currentDate)
            canvas.drawText(currentDate, centerX - dateWidth / 2f, centerY + 100f, clockPaint)

            canvas.restore()
        }
    }
}
