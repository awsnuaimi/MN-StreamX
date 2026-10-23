package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarianModelConfigTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun tokensAndAttentionDimensionsAreReadFromTheSelectedModel() {
        temporary.root.resolve("config.json").writeText("""{"model_type":"marian","vocab_size":5000,
            "d_model":256,"decoder_attention_heads":4,"decoder_start_token_id":4999,
            "eos_token_id":7,"pad_token_id":4998}""")
        assertEquals(MarianModelConfig(5000, 256, 4, 4999, 7, 4998), MarianModelConfig.read(temporary.root))
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidAttentionDimensionsAreRejectedBeforeOpeningNativeSessions() {
        MarianModelConfig(5000, 256, 3, 4999, 0, 4999)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidDecoderStartIdsCannotReachTheNativeTokenizer() {
        MarianModelConfig(5000, 256, 4, 5000, 0, 4999)
    }
}