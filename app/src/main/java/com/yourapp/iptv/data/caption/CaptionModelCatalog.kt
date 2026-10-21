package com.yourapp.iptv.data.caption

/** Public immutable revisions and SHA-256 hashes; models are downloaded only on explicit generation. */
internal object CaptionModelCatalog {
    val speech = ModelBundle("speech-tiny-q5-v1", "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1",
        listOf(ModelAsset("ggml-tiny-q5_1.bin", 32152673L, "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7")))
    val translation = ModelBundle("marian-en-mul-v1", "https://huggingface.co/Xenova/opus-mt-en-mul/resolve/e57f9759f8e1e9ed97067bfc11e53e1eab63e57e",
        listOf(
            ModelAsset("onnx/encoder_model_quantized.onnx", 52443550L, "f10f49c2691043ba7cfd2a85d86f84dddda428d4209cf9b495f6e114b313737a"),
            ModelAsset("onnx/decoder_model_merged_quantized.onnx", 59753048L, "35c4c88823c8fbe44aa57bbe8543abc0c0269f321cbf8075965fd697c9569caa"),
            ModelAsset("source.spm", 790473L, "9129bf81ef9d5fcc1797ea3061e789dc5f9e5602786b0490238d207d4219e0f5"),
            ModelAsset("vocab.json", 1548192L, "6ded021d39a6bd63339a045a0868f69d29aa8f3b6e53f43af2037246a5aeaca0"),
            ModelAsset("config.json", 1390L, "fc12f9f431b4fb8dae1f7776c7ad3f9a5cf0fd6321231e11b45e35a2b11730f3")
        ))
    fun totalBytes(target: String) = speech.bytes + if (target == "en") 0L else translation.bytes
}