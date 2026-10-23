package com.yourapp.iptv.data.caption

/** Language-specific, immutable public exports. Each file is checked before native loading. */
internal object CaptionTranslationCatalog {
    val bundles: Map<String, ModelBundle> = mapOf(
        "ar" to ModelBundle("marian-en-ar-v30", "https://huggingface.co/Xenova/opus-mt-en-ar/resolve/034a684356c19021a187cda4d7f823298b913921",
            listOf(
            ModelAsset("onnx/encoder_model_quantized.onnx", 51773854L, "53c354d87ced44b39085fe8a46a25f09ea4578d442dbce9663f3977228f8a04e"),
            ModelAsset("onnx/decoder_model_merged_quantized.onnx", 59078120L, "c0e553b0c26611780c70417c9b4c1ca7169e9cdb18ba9e649197efb017af9f02"),
            ModelAsset("source.spm", 801074L, "81ac8f4d4ee271f4109eac67560a31f9c2d555463ba5eb95c32c6deee8ae819d"),
            ModelAsset("target.spm", 916890L, "67d0f6afb87768c078ee46c0756b8cc94b1ec23c5526b49ecf6c3b2f9197ec39"),
            ModelAsset("vocab.json", 2243581L, "8b74af9e4c1ec122b8d06362db6c38795e10ca2b0156b2110cf08d4716744b76"),
            ModelAsset("config.json", 1389L, "8135d646cd810090349e8833b7ba593ac3b72d632f8858cb34925bdbcb6a1bea")
        )),
        "de" to ModelBundle("marian-en-de-v30", "https://huggingface.co/Xenova/opus-mt-en-de/resolve/1ca130c44c4c5441ef16d48aae521a424ab644f7",
            listOf(
            ModelAsset("onnx/encoder_model_quantized.onnx", 49366942L, "15834b45fabd2dfb8c6c029b3ca3e7289aeefd90ece798ce42bcf548d1bd3b8d"),
            ModelAsset("onnx/decoder_model_merged_quantized.onnx", 56652404L, "8b46a825964cdd182fe47cc780f0fb87d357eae1f47cf20aa63c1c09be5b510c"),
            ModelAsset("source.spm", 768489L, "678f2a1177d8389f67b66299762dcc4fc567e89b07e212ba91b0c56daecf47ce"),
            ModelAsset("target.spm", 796845L, "bbd1f495eea99c8e21ae086d9146e0fa7b096c3dfdd9ba07ab8b631889df5c9b"),
            ModelAsset("vocab.json", 1389436L, "d5acea957b265a78554999144459c5e391e0df525864edc8287bc090290baa44"),
            ModelAsset("config.json", 1411L, "517955ebf66cd6523f9982851dd41771b74fb2d9c00b4933ba2922ac7a63336f")
        )),
        "pl" to ModelBundle("marian-en-pl-v30", "https://huggingface.co/TigreGotico/opus-mt-en-pl-onnx/resolve/22ac61d6f7a5e58e0e2d0671a5dcff91b9c66f54",
            listOf(
            ModelAsset("int8/encoder_model.onnx", 49586951L, "07005509aee185871eaf3dca95b6aeb4b3477cdd38d3a9d193368a357340677f"),
            ModelAsset("int8/decoder_model.onnx", 56398488L, "a9c9631d53429593a3f4b4eea2b1fd0eab971acbe7a5f30b23e9476e45e1410e"),
            ModelAsset("int8/decoder_with_past_model.onnx", 53186540L, "81a4b670fd8ac507e281e456912bd782e2bb51a9b0ddf1bf5544dba9d3c14fdf"),
            ModelAsset("source.spm", 791358L, "7e262c2e51f67f8ddc3b08ca37381938ecc5cf43b52165054bb965483c3a4e68"),
            ModelAsset("target.spm", 860404L, "948df13e89a108c933ffbf25f620ebf9b817cd68596179284329429469f27f8c"),
            ModelAsset("vocab.json", 1832752L, "3959161e9eeda822624219564a925e4ac79d809326619622228fb3cf2a858b19"),
            ModelAsset("config.json", 1311L, "697e0b5cf9ddf67225a07f5620d0f7fab374098f5b7efe23cecc598a5af3b821")
        )),
        "hi" to ModelBundle("marian-en-hi-v30", "https://huggingface.co/Xenova/opus-mt-en-hi/resolve/7aad72006a1588a9c3485ed6203aed99eebbc872",
            listOf(
            ModelAsset("onnx/encoder_model_quantized.onnx", 51337630L, "3045cd6eaf06db96cff853204d6f0ffeb0c5af238a4f5a87a19fcaa94f7493c4"),
            ModelAsset("onnx/decoder_model_merged_quantized.onnx", 58638488L, "8baba43a14b15ca4543d30ab36e7d34f2e516fb9af7e0b83b77a5e6a6f66d198"),
            ModelAsset("source.spm", 812240L, "fd4e951487aed00bae6a6c2ee4ef5d8d1db05fd098b19b608046c9334b58d24d"),
            ModelAsset("target.spm", 1067935L, "f5529d3a72f8c1d5f7e357f1b6fd30e3cf58f6e1ba0401db135a118ac92f4a76"),
            ModelAsset("vocab.json", 2221738L, "be02f5dfb85054d4f25089f6b347b69d32e98ed4c3d181ed0c789904eb1e3d3b"),
            ModelAsset("config.json", 1389L, "2f7015bbf571018afd1a365d1ade99a648bc89edf3146be93ec0e23cbffd6302")
        ))
    )
    fun forTarget(target: String): ModelBundle = requireNotNull(bundles[target]) { "Unsupported caption target" }
    // Arabic dialects and the Slavic-family export require an explicit target token.
    fun prefix(target: String): String? = when (target) { "ar" -> ">>ara<<"; "pl" -> ">>pol<<"; else -> null }
    fun encoder(bundle: ModelBundle) = bundle.assets.single { it.path.substringAfterLast('/').startsWith("encoder_model") }
    fun decoder(bundle: ModelBundle) = bundle.assets.first { it.path.substringAfterLast('/').startsWith("decoder_model") }
    fun cachedDecoder(bundle: ModelBundle) = bundle.assets.singleOrNull { it.path.contains("decoder_with_past") }
}