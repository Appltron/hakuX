# FEXCore from the FEX fork (branch haku), for the native core and its tests.
#
# Provides the target nc_fex: FEXCore plus FEX's host feature detection
# (taken from its front-end Common library without the rest of it), with the
# directory-scoped definitions FEX sources expect.

set(HAKUX_FEX_DIR "D:/code/FEX" CACHE PATH "Checkout of the FEX fork (branch haku)")

# FEXCore only. No thunks, tests, FEXConfig, telemetry, glibc jemalloc,
# vixl or LTO; FEX's own allocator (rpmalloc) stays on.
set(ENABLE_FEX_ALLOCATOR        ON      CACHE BOOL   "" FORCE)
set(ENABLE_JEMALLOC_GLIBC_ALLOC OFF     CACHE BOOL   "" FORCE)
set(ENABLE_OFFLINE_TELEMETRY    OFF     CACHE BOOL   "" FORCE)
set(BUILD_FEXCONFIG             OFF     CACHE BOOL   "" FORCE)
set(ENABLE_GDB_SYMBOLS          OFF     CACHE BOOL   "" FORCE)
set(ENABLE_LTO                  OFF     CACHE BOOL   "" FORCE)
set(ENABLE_CCACHE               OFF     CACHE BOOL   "" FORCE)
set(BUILD_THUNKS                OFF     CACHE BOOL   "" FORCE)
set(BUILD_TESTING               OFF     CACHE BOOL   "" FORCE)
set(ENABLE_VIXL_DISASSEMBLER    OFF     CACHE BOOL   "" FORCE)
set(ENABLE_VIXL_SIMULATOR       OFF     CACHE BOOL   "" FORCE)
set(BUILD_FEX_LINUX_TESTS       OFF     CACHE BOOL   "" FORCE)
set(ENABLE_ASSERTIONS           OFF     CACHE BOOL   "" FORCE)
set(TUNE_CPU                    none    CACHE STRING "" FORCE)
set(TUNE_ARCH                   generic CACHE STRING "" FORCE)

if(NOT TARGET FEXCore)
  add_subdirectory("${HAKUX_FEX_DIR}" "${CMAKE_BINARY_DIR}/fex-build" EXCLUDE_FROM_ALL)
endif()

if(NOT TARGET nc_fex)
  add_library(nc_fex STATIC
    "${HAKUX_FEX_DIR}/Source/Common/HostFeatures.cpp"
    "${HAKUX_FEX_DIR}/Source/Common/CPUInfo.cpp"
    "${HAKUX_FEX_DIR}/Source/Common/Linux/LinuxVersion.cpp"
  )
  target_compile_features(nc_fex PUBLIC cxx_std_20)
  target_include_directories(nc_fex PUBLIC
    "${HAKUX_FEX_DIR}/Source"
    "${CMAKE_BINARY_DIR}/fex-build/Source"
  )
  # FEX sets these per directory (add_compile_definitions), so sources built
  # outside its tree need them repeated; the NDK's clang has preserve_all.
  target_compile_definitions(nc_fex PUBLIC
    ARCHITECTURE_arm64=1 _LARGEFILE64_SOURCE
    "FEX_PRESERVE_ALL_ATTR=__attribute__((preserve_all))" FEX_HAS_PRESERVE_ALL_ATTR=1
  )
  target_link_libraries(nc_fex PUBLIC FEXCore FEXCore_Base JemallocLibs FEXHeaderUtils range-v3::range-v3 log)
endif()
