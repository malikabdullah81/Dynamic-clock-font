package com.malikabdullah81.dynamicclockfont

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
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

        // One UI 8.5 Screen-on dot-to-expanded bloom animation
        private var animProgress = 1f
        private val revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { animator ->
                animProgress = animator.animatedValue as Float
                if (visible) {
                    drawFrame()
                }
            }
        }

        // Broadcast receiver to catch screen wake events on Samsung One UI
        private val screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    triggerBloomAnimation()
                }
            }
        }
        private var receiverRegistered = false

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
                    handler.postDelayed(this, 1000L)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            // Register screen-on receiver
            try {
                val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
                registerReceiver(screenOnReceiver, filter)
                receiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun triggerBloomAnimation() {
            animProgress = 0f
            revealAnimator.cancel()
            revealAnimator.start()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                triggerBloomAnimation()
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
                revealAnimator.cancel()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
            revealAnimator.cancel()
        }

        override fun onDestroy() {
            super.onDestroy()
            if (receiverRegistered) {
                try {
                    unregisterReceiver(screenOnReceiver)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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

            canvas.drawColor(Color.BLACK)
            canvas.save()

            // Screen-on bloom scaling effect (blooms from a tiny dot to full size)
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
