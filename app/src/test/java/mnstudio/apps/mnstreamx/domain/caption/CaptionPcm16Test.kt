package mnstudio.apps.mnstreamx.domain.caption
import org.junit.Assert.*
import org.junit.Test
class CaptionPcm16Test {
    @Test fun everyPcm16AmplitudeRoundTripsThePlayerNormalization() {
        val original = ShortArray(65536) { (it - 32768).toShort() }
        val normalized = FloatArray(original.size) { original[it] / 32768f }
        assertArrayEquals(original, CaptionPcm16.shorts(normalized, 0, normalized.size))
    }
    @Test fun chunksHonorOffsetsAndSanitizeInvalidSamples() {
        val input = floatArrayOf(0.1f, -1f, 1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.5f)
        assertArrayEquals(shortArrayOf(-32768, 32767, 0, 0, 0), CaptionPcm16.shorts(input, 1, 5))
    }
    @Test(expected = IllegalArgumentException::class)
    fun cannotReadPastWindow() { CaptionPcm16.shorts(FloatArray(4), 2, 3) }
}