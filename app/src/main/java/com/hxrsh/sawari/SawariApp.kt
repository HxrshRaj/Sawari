package com.hxrsh.sawari

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SawariApp : Application(), ImageLoaderFactory {
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
