package com.yourapp.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.yourapp.iptv.data.image.AppImageLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = AppImageLoader.create(this)
}