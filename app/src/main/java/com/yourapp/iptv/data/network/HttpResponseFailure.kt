package com.yourapp.iptv.data.network

import java.io.IOException

internal class HttpResponseFailure(val status: Int) : IOException("HTTP $status")
internal class ResponseLimitFailure : IOException("Response limit exceeded")