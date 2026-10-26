package com.yourapp.iptv.domain.model

import java.io.IOException

class PlaylistLoadException @JvmOverloads constructor(message: String, cause: Throwable? = null) : IOException(message,cause)