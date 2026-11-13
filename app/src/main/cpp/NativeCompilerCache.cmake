# Use CMake's compiler launcher rather than wrapping the NDK toolchain itself.
# Targets created after this include inherit the launcher (including vendored libs).
# Off by default; if ccache cannot be found, compile directly as before.
if("$ENV{MN_STREAMX_ENABLE_CCACHE}" STREQUAL "1")
    find_program(MNX_CCACHE_EXECUTABLE NAMES ccache)
    if(MNX_CCACHE_EXECUTABLE)
        set(CMAKE_C_COMPILER_LAUNCHER "${MNX_CCACHE_EXECUTABLE}")
        set(CMAKE_CXX_COMPILER_LAUNCHER "${MNX_CCACHE_EXECUTABLE}")
        message(STATUS "MN StreamX: native compiler cache active")
    else()
        message(WARNING "MN StreamX: ccache requested but not available; building without it")
    endif()
endif()