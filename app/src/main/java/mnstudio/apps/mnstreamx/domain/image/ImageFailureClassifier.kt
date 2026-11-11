package mnstudio.apps.mnstreamx.domain.image

import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Coarse, privacy-safe categories; never records the exception, message, URL or hostname. */
internal object ImageFailureClassifier {
    fun classify(error: Throwable): ImageFailureCategory {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth++ < 6) {
            when (current) {
                is InterruptedIOException -> return ImageFailureCategory.TIMEOUT
                is UnknownHostException, is ConnectException, is SSLException ->
                    return ImageFailureCategory.NETWORK
                is FileNotFoundException -> return ImageFailureCategory.MISSING_DATA
            }
            // Coil/network and image decoder exceptions are not required for the domain module.
            when (current.javaClass.simpleName) {
                "HttpException" -> return ImageFailureCategory.HTTP
                "DecodeException", "ImageDecoderException", "BitmapFactoryException" ->
                    return ImageFailureCategory.DECODE
                "NullRequestDataException" -> return ImageFailureCategory.MISSING_DATA
            }
            val next = current.cause
            if (next === current) break
            current = next
        }
        return ImageFailureCategory.OTHER
    }
}