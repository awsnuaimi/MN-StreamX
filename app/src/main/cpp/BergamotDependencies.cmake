include(FetchContent)
function(mnx_source name url digest)
    if(MNX_NATIVE_SOURCES AND IS_DIRECTORY "${MNX_NATIVE_SOURCES}/${name}")
        set(${name}_SOURCE_DIR "${MNX_NATIVE_SOURCES}/${name}" PARENT_SCOPE)
        return()
    endif()
    FetchContent_Declare(mnx_${name} URL "${url}" URL_HASH SHA256=${digest})
    FetchContent_GetProperties(mnx_${name})
    if(NOT mnx_${name}_POPULATED)
        FetchContent_Populate(mnx_${name})
    endif()
    set(${name}_SOURCE_DIR "${mnx_${name}_SOURCE_DIR}" PARENT_SCOPE)
endfunction()
mnx_source(bergamot https://codeload.github.com/mozilla/translations/tar.gz/8eada038fd45bbf6fa58a412b22f80c79887fabc
    08e1ab2e24e70439d30c5820b83b09ce0cfa874086b998f5113e99c9b12c1329)
mnx_source(ssplit https://codeload.github.com/browsermt/ssplit-cpp/tar.gz/a311f9865ade34db1e8e080e6cc146f55dafb067
    937ded1229ee69f340772056a4ff6daaede5b899f12c4c7b091c40f430c56fd8)
mnx_source(sentencepiece https://codeload.github.com/browsermt/sentencepiece/tar.gz/ae41b7740d7006596bb9257e83340b2620db9d00
    4d2148ae7e240cd113586004d2cf0102f7ab1bae363b91b2934ee83c5ae3dbbe)
mnx_source(intgemm https://codeload.github.com/kpu/intgemm/tar.gz/f7401513da71758dacce52fed1c7855549abee59
    8e827c445dbb1b117a9667616bac23b3c945e626da0a3466786cfd058ed2f41d)
mnx_source(ruy https://codeload.github.com/google/ruy/tar.gz/2d950b3bfa7ebfbe7a97ecb44b1cc4da5ac1d6f0
    6593ec4323ae69e542bde97711d5680069cb802301512063289b1103c5374244)
mnx_source(simd_utils https://codeload.github.com/browsermt/simd_utils/tar.gz/d0793d86aea9036a5bc77b9ca7791dff024168ca
    829325e164ee2be5dffed51ccf8cc4b776edbb4269d174e79f6708fd89fca59f)
mnx_source(pcre2 https://codeload.github.com/PCRE2Project/pcre2/tar.gz/pcre2-10.44
    07a002e8216382a96f722bc4a831f3d77457fe3e9e62a6dff250a2dd0e9c5e6d)
mnx_source(zstd https://codeload.github.com/facebook/zstd/tar.gz/v1.5.7
    37d7284556b20954e56e1ca85b80226768902e2edabd3b649e9e72c0c9012ee3)
mnx_source(simde https://codeload.github.com/simd-everywhere/simde/tar.gz/v0.8.2
    ed2a3268658f2f2a9b5367628a85ccd4cf9516460ed8604eed369653d49b25fb)
mnx_source(cpuinfo https://codeload.github.com/pytorch/cpuinfo/tar.gz/5916273f79a21551890fd3d56fc5375a78d1598d
    f3c16d5d393d6d1fa6b6ed8621dd0a535552df9bc88cbba739375dde38a93142)