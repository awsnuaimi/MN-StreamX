package mnstudio.apps.mnstreamx.data.caption
internal data class PackedTranslationAsset(val packed: ModelAsset, val name: String, val size: Long, val sha256: String, val kind: String)
internal data class BergamotPack(val source: String, val target: String, val assets: List<PackedTranslationAsset>) {
    val id get() = "bergamot-$source-$target-v3"
    val bundle get() = ModelBundle(id, "https://firefox-settings-attachments.cdn.mozilla.net/main-workspace/translations-models-v2", assets.map { it.packed })
    fun file(kind: String) = assets.single { it.kind == kind }.name
}
internal object BergamotCatalog {
    fun pack(source: String, target: String) = packs.firstOrNull { it.source == source && it.target == target }
        ?: throw IllegalArgumentException("Unsupported local text direction")
    val packs = listOf(
        BergamotPack("en", "ar", listOf(
            PackedTranslationAsset(ModelAsset("6b7c1061-729a-44e3-858d-409a7061a704.zst", 1204697L, "f5fe1d8d145e38b4c6fd9f7a35d0d5779388fa135743561e1d25e982d7191222"),
                "lex.50.50.enar.s2t.bin", 3139692L, "2b7194817c5dd9225ca90c0d908cc92e1d6d1f45625781a5feb037ac568d6a61", "lex"),
            PackedTranslationAsset(ModelAsset("0d56ac93-c0ac-4854-b48e-09e38d51dace.zst", 22272394L, "41dc919c1110508ad7f03ee53bbb28a54abd61d1b15bbc3fe51c5df5c9331016"),
                "model.enar.intgemm.alphas.bin", 31561787L, "ae659d7045fc2e5d6ba50583586a5d94ee4358d92c019847e37b57a0e627faa8", "model"),
            PackedTranslationAsset(ModelAsset("371728a2-8c02-4528-bd76-4cd1c4e8f05e.zst", 351089L, "9f68a65a64d4c64ea41d94024160e1443ff51b6c7ba0a2f93e7702beaa0b1d25"),
                "vocab.enar.spm", 863591L, "8d93c54aa5e2044c416ec680b5ff9af0227bd698521666e8b1a1ea1b041fbae8", "vocab")
        )),
        BergamotPack("ar", "en", listOf(
            PackedTranslationAsset(ModelAsset("5fef8e32-9215-4eda-b63d-b4105b9820f6.zst", 1623998L, "0dc3d51a8e41da0ee6f53784e04b2cd044e20e15f15b09aa9c1d96047de803b4"),
                "lex.50.50.aren.s2t.bin", 4627200L, "4b45f14dbea40d368093a13563fc1ca48457ee70c1291c82b206f3baff210081", "lex"),
            PackedTranslationAsset(ModelAsset("c1fc44d3-7b80-415e-9b74-57c4ee027559.zst", 22770191L, "beed6b0da7378dfe256a9c0604a0d07dcb2b9ad4ce5295ac1ea6f1c5815eed65"),
                "model.aren.intgemm.alphas.bin", 31561787L, "7b7af0282dc5f4d8805b9a298c2fa828967e3f09ca10f1942ebeea0b2cfc12fa", "model"),
            PackedTranslationAsset(ModelAsset("324af319-7174-4574-a0e5-dc047a78aa33.zst", 349058L, "4e7256a9b84ae2773a5b32a234418d1c111a018b9e1bf2580ad83972d5166687"),
                "vocab.aren.spm", 860035L, "51b4ee3f828d10015464523d0b4f5a6c086b0b2a9bde716a581001cf6c260366", "vocab")
        )),
        BergamotPack("en", "de", listOf(
            PackedTranslationAsset(ModelAsset("7b8929f7-c5c2-4869-9193-12821a86354d.zst", 1696246L, "a483b34d541dc8d4097cbfafbb188d68ee54d68368b96ed2e3d1e16bcfad40aa"),
                "lex.50.50.ende.s2t.bin", 4347672L, "7ed39f1cffbd68a27ddf05bbfe068de2060f1d7e69f1a20e27ae923551dd7393", "lex"),
            PackedTranslationAsset(ModelAsset("210d5dea-12cc-4960-9f6a-aa21740c6735.zst", 22461496L, "db0fa25f3767efdf913ad7d213adf51702a87fb9b07f5e6c64fc4dc13c2eb2cf"),
                "model.ende.intgemm.alphas.bin", 31561787L, "8df29d9494d19f47fd5d97c6a73474c6f657e9f81c1a607c431d02befdf3810f", "model"),
            PackedTranslationAsset(ModelAsset("a850d21e-c220-45ea-b978-a826a9248e64.zst", 352776L, "688bd61fce9ff1a1f93b81f0f039f9a850a3f345b0a82e044a743ff48b71ffbc"),
                "vocab.ende.spm", 810073L, "69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c", "vocab")
        )),
        BergamotPack("de", "en", listOf(
            PackedTranslationAsset(ModelAsset("87ba9213-f69e-4e31-9251-aa2e297d1f26.zst", 1953706L, "a308bd1cae2bf372a352d2e37b9ba286616d2cf386d14bdf848b54f8eb1d7408"),
                "lex.50.50.deen.s2t.bin", 4945796L, "113b98460468360cca68c042e1cddf49c4e1931cbb975ed04349c9a3bd607010", "lex"),
            PackedTranslationAsset(ModelAsset("172680a0-ffd1-4c00-8b35-216898c4f462.zst", 21611407L, "1a1d3db174f2e555a2d5d11abc303cc839a84dace2de1dcc46d65e3a217d3a7c"),
                "model.deen.intgemm.alphas.bin", 31561787L, "3e6f7c2c2425d10824797270b382bee718ff34af2cab9308841c82ca46dc6f20", "model"),
            PackedTranslationAsset(ModelAsset("3187809f-ec68-4da1-8149-f9c4b0f36644.zst", 352776L, "688bd61fce9ff1a1f93b81f0f039f9a850a3f345b0a82e044a743ff48b71ffbc"),
                "vocab.deen.spm", 810073L, "69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c", "vocab")
        )),
        BergamotPack("en", "pl", listOf(
            PackedTranslationAsset(ModelAsset("0c4c3c04-92c0-495d-9216-5ee715575bc1.zst", 1379394L, "9934637772a0408e97b767b87580bd56678bbde6a1b9f73afd503aad174cba60"),
                "lex.50.50.enpl.s2t.bin", 3540052L, "d7d7b364673f633763c9f8f2e8ee9c7a184e92f4086e8b84abe870a2a8dbe5d5", "lex"),
            PackedTranslationAsset(ModelAsset("251fa2ec-4d4e-4c44-8520-8c5c35d967a0.zst", 21869196L, "4c3e2a71b7f801eeb81394b3b4a66189b4ee850cf7202855157776c7fa55bf0b"),
                "model.enpl.intgemm.alphas.bin", 31561787L, "b0b59716bcc8f46b8dc6ab5c32a2732acaad42a5a6d9ae9b472526a1bb0d71d4", "model"),
            PackedTranslationAsset(ModelAsset("900a6ffd-7ef3-4e05-8a2b-236d4ef616d3.zst", 351738L, "7926a0be1849310fe58241d9b9539fe65f796dffcc7a8c52daa4a78234ebb2c0"),
                "vocab.enpl.spm", 821612L, "2000f182efa8ce5f69bc3f9e574666b5bc2c0af684eedd32f69d15aa9021ea85", "vocab")
        )),
        BergamotPack("pl", "en", listOf(
            PackedTranslationAsset(ModelAsset("bf697ced-51fc-43c9-8600-51d7e2109505.zst", 1809189L, "9ba237b1684337cd937e20205d2947fec2db65c08fa17b555e52236e87a41b09"),
                "lex.50.50.plen.s2t.bin", 4964208L, "b295dfefa7022a62e105276e8c39fdfd67bffe588f4fd78583edd4e04ee0cb3b", "lex"),
            PackedTranslationAsset(ModelAsset("5e139d66-ff65-435b-8b43-3602b0b492cf.zst", 21997629L, "bac2aaa11d0dd40aae999929702b3e97dd4c6e8780920a55edb58b40705593e5"),
                "model.plen.intgemm.alphas.bin", 31561787L, "06dbab3baa3bbceab746bc6a3bba807878f38536382c03acddf59d3edf3b5d94", "model"),
            PackedTranslationAsset(ModelAsset("a28acc03-8d4b-468e-a781-67f395ce66c6.zst", 351738L, "7926a0be1849310fe58241d9b9539fe65f796dffcc7a8c52daa4a78234ebb2c0"),
                "vocab.plen.spm", 821612L, "2000f182efa8ce5f69bc3f9e574666b5bc2c0af684eedd32f69d15aa9021ea85", "vocab")
        )),
        BergamotPack("en", "hi", listOf(
            PackedTranslationAsset(ModelAsset("f8ac474a-201d-4bb5-873f-2afe2e166c15.zst", 1264527L, "b617f6ab1f16cd730f413e0ceba4f67717db552e6b9d85b38fa132a9e6ed64d2"),
                "lex.50.50.enhi.s2t.bin", 3504820L, "b473f6fe41cdb7c1dbeeb714fc50314feab77b0af2b2da6f7830ee14492dd847", "lex"),
            PackedTranslationAsset(ModelAsset("76be957c-5fc4-4fc1-8fd3-08e5eb1d69b7.zst", 12548777L, "4d2cdcbd26a976bd9b1aa9173651544a075700d3f55e638fa88ae4d0f9d53b01"),
                "model.enhi.intgemm.alphas.bin", 17141051L, "18682fa6aa1c1b2f78a6fcbcb354755094d623e65436ef7821720c5213e11a49", "model"),
            PackedTranslationAsset(ModelAsset("e025c1a4-22a0-47e9-83bd-a765425cc569.zst", 360925L, "927b885d1e2fc2cc1d5fee6cafd024511a04d5cb0b7da80e45989b5f17ded22e"),
                "vocab.enhi.spm", 925360L, "8017cbf812bae400794c424699b89de90db06ac7a4ca67a420013a9f68b270c1", "vocab")
        )),
        BergamotPack("hi", "en", listOf(
            PackedTranslationAsset(ModelAsset("c42c6e9f-bc79-404b-acf7-6a38b66fb436.zst", 1716252L, "631ad58faa9674014dc386afe60c4a9ab9cda9fc706f1a86d92bd31195898c12"),
                "lex.50.50.hien.s2t.bin", 4605984L, "e789be33dfdb639e98f5cf9eb01aa945ec3c882f68f3c8860389cf846f0112d8", "lex"),
            PackedTranslationAsset(ModelAsset("4d5cef3e-98c8-449b-817b-01c4352e1708.zst", 12553326L, "7a5d9ba3fb726e7b06c091598b7862c7c961108fc17a340bd88b897c031351c3"),
                "model.hien.intgemm.alphas.bin", 17141051L, "c82fd6ed3794b79fb9dde0718a22324502a408a9eca73497b5d0a4b8024ec6de", "model"),
            PackedTranslationAsset(ModelAsset("56bd41a2-bb65-4751-89ec-47c88c144034.zst", 361002L, "11197b63e7063d17b01f2199c6c402a203b2a950551e72cde590774aa2f37dbf"),
                "vocab.hien.spm", 925436L, "9cbc02094ac2ebd75211b3bb6789a9d6fa112ce3c08e897f1645f18b21fbab00", "vocab")
        ))
    )
}