package com.hxrsh.sawari

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SawariApp : Application(), ImageLoaderFactory, CameraXConfig.Provider {
    // The notification channel is deliberately NOT created here. On Android 13+ creating a channel makes the system show
    // its own "Allow notifications?" prompt at first launch, which would skip our explain-first step. It is created when the
    // user chooses to turn reminders on, or when a reminder is actually shown.

    /**
     * The app only ever uses the back camera (licence photo). Limiting CameraX to it skips validating cameras we do not
     * use: without this, a device that declares a front camera it does not have (like the stock emulator) makes CameraX
     * fail to initialise at all ("Expected camera missing from device").
     */
    override fun getCameraXConfig(): CameraXConfig =
        CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .build()

    /**
     * One app-wide image pipeline tuned for small devices: bounded memory cache, bounded disk cache and, on
     * low-RAM devices, RGB_565 bitmaps (half the memory of ARGB_8888; the photos have no alpha).
     */
    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowRam = am.isLowRamDevice
        return ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (lowRam) 0.10 else 0.20).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(50L * 1024 * 1024).build() }
            .bitmapConfig(if (lowRam) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
