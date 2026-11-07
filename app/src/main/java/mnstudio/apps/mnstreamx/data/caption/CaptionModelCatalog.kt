package mnstudio.apps.mnstreamx.data.caption

/** Public immutable revisions and SHA-256 hashes; models are downloaded only on explicit generation. */
internal object CaptionModelCatalog {
    val vad = ModelBundle("silero-vad-v5-1-2", "https://raw.githubusercontent.com/snakers4/silero-vad/6478567951ae5c9979ad7b234185b5515f4be7a1",
        listOf(ModelAsset("src/silero_vad/data/silero_vad.onnx", 2327524L,
            "2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f")))
    val speech = ModelBundle("speech-base-q5-v1", "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1",
        listOf(ModelAsset("ggml-base-q5_1.bin", 59707625L, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898")))
    val translation = ModelBundle("marian-en-mul-v1", "https://huggingface.co/Xenova/opus-mt-en-mul/resolve/e57f9759f8e1e9ed97067bfc11e53e1eab63e57e",
        listOf(
            ModelAsset("onnx/encoder_model_quantized.onnx", 52443550L, "f10f49c2691043ba7cfd2a85d86f84dddda428d4209cf9b495f6e114b313737a"),
            ModelAsset("onnx/decoder_model_merged_quantized.onnx", 59753048L, "35c4c88823c8fbe44aa57bbe8543abc0c0269f321cbf8075965fd697c9569caa"),
            ModelAsset("source.spm", 790473L, "9129bf81ef9d5fcc1797ea3061e789dc5f9e5602786b0490238d207d4219e0f5"),
            ModelAsset("vocab.json", 1548192L, "6ded021d39a6bd63339a045a0868f69d29aa8f3b6e53f43af2037246a5aeaca0"),
            ModelAsset("config.json", 1390L, "fc12f9f431b4fb8dae1f7776c7ad3f9a5cf0fd6321231e11b45e35a2b11730f3")
        ))
    fun totalBytes(target: String, source: String? = null) = CaptionSpeechPlan.downloadBytes(source ?: "auto", target)
}