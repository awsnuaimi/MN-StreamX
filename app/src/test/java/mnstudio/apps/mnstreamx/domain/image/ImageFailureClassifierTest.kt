package mnstudio.apps.mnstreamx.domain.image

import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class ImageFailureClassifierTest {
    private class HttpException : Exception()
    private class DecodeException : Exception()

    @Test fun classifiesKnownTypesWithoutReadingMessagesOrUrls() {
        assertEquals(ImageFailureCategory.TIMEOUT,
            ImageFailureClassifier.classify(RuntimeException("secret.example", SocketTimeoutException("host"))))
        assertEquals(ImageFailureCategory.NETWORK,
            ImageFailureClassifier.classify(UnknownHostException("sensitive.provider.test")))
        assertEquals(ImageFailureCategory.HTTP, ImageFailureClassifier.classify(HttpException()))
        assertEquals(ImageFailureCategory.DECODE, ImageFailureClassifier.classify(DecodeException()))
        assertEquals(ImageFailureCategory.MISSING_DATA,
            ImageFailureClassifier.classify(FileNotFoundException("private-file")))
        assertEquals(ImageFailureCategory.OTHER, ImageFailureClassifier.classify(IllegalArgumentException("secret")))
    }

    @Test fun doesNotTraverseUnboundedThrowableChains() {
        var error: Throwable = SocketTimeoutException("private")
        repeat(8) { error = IllegalStateException("private", error) }
        assertEquals(ImageFailureCategory.OTHER, ImageFailureClassifier.classify(error))
    }
}