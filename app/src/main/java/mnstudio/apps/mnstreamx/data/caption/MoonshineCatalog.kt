package mnstudio.apps.mnstreamx.data.caption
/** MIT Tiny Streaming family only; no legacy non-commercial language models. */
internal object MoonshineCatalog {
    fun forSource(source: String): ModelBundle = when (source) {
        "ar" -> ModelBundle("moonshine-ar-stream-v1", "https://download.moonshine.ai/model/tiny-streaming-ar/quantized_26_08_24", listOf(
            ModelAsset("adapter.ort", 1318472L, "9c1278a338b4ff268fc5741be01446d56ca9cba9ff5f4a75c5e1b2c2e2739361"),
            ModelAsset("cross_kv.ort", 1288120L, "e317af289233894f49da429d4561150c2f61ca86c1b9ca91c6edd3fb4fdeecb5"),
            ModelAsset("decoder_kv.ort", 19717336L, "5c9a2bf2d9fc543fd4596b3ab4ab8d09f791aac5682eb1379fd8d59e34237db0"),
            ModelAsset("encoder.ort", 7772792L, "6785ca9932d59265375249a8f3bc62f8333fb4dee6ab7db8dfd6f1daef66f59e"),
            ModelAsset("frontend.model.ort", 23176L, "04c552e66efe3f7206d0a69779864f93bf3cfaf2fa595c9ce5101c8e3854a414"),
            ModelAsset("frontend.weights.ort", 2093280L, "6cc4d70efffe757452996574d11065e7dac6a7ff0a9be3e2fec1d0266b062b0e"),
            ModelAsset("streaming_config.json", 509L, "d31afd7c2dd5ee9ae3fa232312c5b42cb3e4fe1c6e9c53d885f2bea63023790a"),
            ModelAsset("tokenizer.bin", 135726L, "3acea39a89cdf44fa11f10bfdd7d05d9015d901999f8c8fa18988f19dcc6b267")
        ))
        "de" -> ModelBundle("moonshine-de-stream-v1", "https://download.moonshine.ai/model/tiny-streaming-de/quantized_26_08_24", listOf(
            ModelAsset("adapter.ort", 1318472L, "23b91142a35cfbc533a7f03ad8387d0b6d32856b465c6fe7a14ede44a254a345"),
            ModelAsset("cross_kv.ort", 1288120L, "25e6d9d4f16569c2ede7e23a381fe7c60cc4b0f141adc88580557d4530634f17"),
            ModelAsset("decoder_kv.ort", 19717336L, "768b1464074fc4fb7327e8b863fc13dcd12cb7bbe8d8e9428fa7292f7fd9ca6e"),
            ModelAsset("encoder.ort", 7772792L, "74610b818058e396f4b9d9ffd730cf8e36772122464d989dd3011dfa76bd6baf"),
            ModelAsset("frontend.model.ort", 23176L, "4445bb4c2f41b0dd993999e06dc9367a0dde0d42a2e777116e7d93be102dc43d"),
            ModelAsset("frontend.weights.ort", 2093280L, "233fda61d583403bbf13ea71b6ba2335fdf57eac71be85a97dcbb90a6c2f4186"),
            ModelAsset("streaming_config.json", 509L, "d31afd7c2dd5ee9ae3fa232312c5b42cb3e4fe1c6e9c53d885f2bea63023790a"),
            ModelAsset("tokenizer.bin", 103319L, "5725ad3771a1339802f8cebeda0fa8f7723f5282b1ee726c2544774d7399b9a7")
        ))
        "en" -> ModelBundle("moonshine-en-stream-v1", "https://download.moonshine.ai/model/tiny-streaming-en/quantized_26_11_02", listOf(
            ModelAsset("adapter.ort", 1319616L, "c5f9f7cc98c27a965d8e200816f7e5c07b892712bb30cf07444bbde4ffd38199"),
            ModelAsset("cross_kv.ort", 1287504L, "5f68900da4ea20f40fbe67bbec36399f0d47faedbd7198b29abd9dc5f472ef9f"),
            ModelAsset("decoder_kv.ort", 32748720L, "6564f2c9a19de44195a53e336e07d05108f14babab57105d623bb68b79572d75"),
            ModelAsset("encoder.ort", 7675392L, "617e403b2aae48f2474e0b148bf835deb9510bc8692efeabfe27203959a3256e"),
            ModelAsset("frontend.model.ort", 23392L, "5662679f0e5758646d8eade3bc4b5d9ebda875feb6e696c0cc5f1a08fea2219e"),
            ModelAsset("frontend.weights.ort", 2093416L, "55c91d7efab9f7506d7d753131cba72145ff016be3447319fe414a31e13f25b4"),
            ModelAsset("streaming_config.json", 509L, "74fe5ddebd63b17caf59e8a3b18c17547ff7bce1642050edbb1c3962674f8950"),
            ModelAsset("tokenizer.bin", 249974L, "6884b35fd6377d4c4d32336a0bc152f36b64d1e45b6503683cdc238250a8472d")
        ))
        else -> throw IllegalArgumentException("No speech pack for this language")
    }
    fun sourceForFolder(name: String) = listOf("ar", "de", "en").firstOrNull { "moonshine-$it-stream-v1" == name }
}