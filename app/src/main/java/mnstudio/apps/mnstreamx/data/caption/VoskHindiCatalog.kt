package mnstudio.apps.mnstreamx.data.caption
internal object VoskHindiCatalog {
    const val FOLDER = "vosk-model-small-hi-0.22"
    val bundle = ModelBundle("vosk-hi-small-v1", "https://alphacephei.com/vosk/models", listOf(
        ModelAsset("vosk-model-small-hi-0.22.zip", 44458845L, "7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48")
    ))
    val files = listOf(
        ModelAsset("graph/phones/word_boundary.int", 2481L, "b8f981d84d8765fb15003ac54ec0f9599efa65a00d6d4d60086e5969ad45ff01"),
        ModelAsset("graph/Gr.fst", 30692365L, "9b219e22fc7d3033ba1b175757a700d8f02139d75d7f57cc12bc318d53038ccf"),
        ModelAsset("graph/HCLr.fst", 25289514L, "4f36aad2566853473603abc66fac3985eff0860229c465540b348c22fcbbb4f5"),
        ModelAsset("graph/disambig_tid.int", 66L, "3f87fd5a92742b3827d2da31dae0874c4077e9f0011e507423b6f876d743e37b"),
        ModelAsset("am/final.mdl", 15957444L, "c5d7ea51a074bc64430137a74fc8a0a06097df485a3d356cf37d2d78ef6bd712"),
        ModelAsset("README", 460L, "9ffae7d857a7671a0a53b9f6f432e7c312459fde02ca1f139d808fffc3d2644a"),
        ModelAsset("conf/model.conf", 289L, "d8d91d310a9659fed2d89f8ea4996af6163a43c3ab008cd278e4264120551712"),
        ModelAsset("conf/mfcc.conf", 128L, "31dab8bb1dea9b33e039988ac079dfe5d50ebaa4a94826b9f9adc8fe84b147c6"),
        ModelAsset("ivector/final.dubm", 168048L, "e495541a7e99e78a42cd098adfd94a72b6118a8c0d3c5ea8fe17436b578c55d4"),
        ModelAsset("ivector/global_cmvn.stats", 1074L, "a056b7592116d4a5e5fc6f872f8398190878bb6690bf486827fa79dabaf33f97"),
        ModelAsset("ivector/final.ie", 9927287L, "89d91ca6e99e7e4f067b3ef8ac1faa9ba73276c225d927aaa296492a03a639b7"),
        ModelAsset("ivector/final.mat", 44975L, "3009aea21b59ad36f86c89d68dd152264aa3a784260dbdace823d991f3ff71b4"),
        ModelAsset("ivector/splice.conf", 35L, "9f0c5f7c82d18eaf25d8bce470efa9f7741f88411fe428774bc0a9bb69a24756"),
        ModelAsset("ivector/online_cmvn.conf", 0L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    )
}