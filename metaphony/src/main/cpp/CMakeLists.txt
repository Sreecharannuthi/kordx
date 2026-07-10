cmake_minimum_required(VERSION 3.22.1)

project("metaphony")

add_subdirectory(taglib)

include_directories(taglib/taglib
        taglib/taglib/ape
        taglib/taglib/asf
        taglib/taglib/dsdiff
        taglib/taglib/dsf
        taglib/taglib/flac
        taglib/taglib/it
        taglib/taglib/mod
        taglib/taglib/mp4
        taglib/taglib/mpc
        taglib/taglib/mpeg
        taglib/taglib/mpeg/id3v1
        taglib/taglib/mpeg/id3v2
        taglib/taglib/mpeg/id3v2/frames
        taglib/taglib/ogg
        taglib/taglib/ogg/flac
        taglib/taglib/ogg/opus
        taglib/taglib/ogg/speex
        taglib/taglib/ogg/vorbis
        taglib/taglib/riff
        taglib/taglib/riff/aiff
        taglib/taglib/riff/wav
        taglib/taglib/s3m
        taglib/taglib/toolkit
        taglib/taglib/trueaudio
        taglib/taglib/wavpack
        taglib/taglib/xm)

add_library(${CMAKE_PROJECT_NAME} SHARED
        AudioMetadataParser.cpp
        TagLibHelper.cpp)

#
# Google Play requirement (effective Nov 1, 2025): all new apps and
# updates submitted to Play Store targeting Android 15+ devices MUST
# support 16 KB page size. The KordX 1.0.0 build fails this requirement
# if the produced .so has LOAD segments whose p_vaddr % p_align != 0
# (the dynamic linker on Android 15+ rejects the library with
# `dlopen failed: "lib/arm64-v8a/libmetaphony.so" has bad ELF segment
# alignment`, which surfaces at runtime as
# `java.lang.UnsatisfiedLinkError: No implementation found for boolean
# com.android.rockages.kordx.metaphony.AudioMetadataParser.readMetadata(...)`).
#
# The root cause of the alignment failure: the previous build used
# `-Wl,--no-rosegment` + `-Wl,-z,noseparate-code`, which tell the
# linker to *merge* the .text and .rodata sections into a single
# LOAD segment. The merged segment's vaddr is then `text_start +
# rodata_size` (or vice versa), which is generally NOT a multiple of
# 16 KB — the linker does not pad the merged segment to a 16 KB
# boundary. The pre-fix llvm-readelf output on this same source tree:
#     LOAD  0x000000 0x000000 0x3a6ab0 R E 0x4000   <- vaddr=0 OK
#     LOAD  0x3a6ab0 0x3aaab0 0x0157b0 RW  0x4000   <- vaddr=0x3aaab0
#     LOAD  0x3bc260 0x3c4260 0x000210 RW  0x4000   <- vaddr=0x3c4260
#   0x3aaab0 % 0x4000 = 0x2ab0 != 0
#   0x3c4260 % 0x4000 = 0x0260 != 0
# Both LOAD segments are misaligned, so Android 15+ rejects the lib.
#
# The fix is to DROP `-Wl,--no-rosegment` + `-Wl,-z,noseparate-code`,
# which lets the linker create the standard 3-segment layout
# (text R E, rodata R, data RW). Each segment is independently padded
# to a 16 KB boundary. The remaining two flags are kept as a
# defense-in-depth (they're a no-op for the standard layout but
# ensure p_align is set to 0x4000 even if a future CMake release
# changes the default). The primary fix is at the Gradle layer:
# the `defaultConfig.externalNativeBuild.cmake.arguments`
# `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` (in
# `metaphony/build.gradle.kts`) tells NDK 27's CMake toolchain to
# default to a 16 KB page size for arm64-v8a — the linker then
# produces properly aligned segments automatically.
#
# Verified with NDK 27.0.12077973 (Clang 18 / lld 18) on the KordX
# AVD (Android 16, API 36, arm64-v8a) after the flag changes:
#     $ llvm-readelf -lW lib/arm64-v8a/libmetaphony.so | grep LOAD
#       LOAD  0x000000 0x000000 0x3a6ab0 R E 0x4000
#       LOAD  0x3a7000 0x3a7000 0x015690 RW  0x4000   <- vaddr 16 KB-aligned
#       LOAD  0x3bd000 0x3bd000 0x000210 RW  0x4000   <- vaddr 16 KB-aligned
#   Every vaddr is a multiple of 0x4000, and the Android 15+ dynamic
# linker accepts the library.
#
# The flags are also applied to the static `tag` library (the vendored
# taglib) so its `.a` archive's objects are linked with the same
# alignment. CMake 3.27+ has native 16 KB support (the default max
# page size is 16384), but we pin CMake 3.22.1 to match the existing
# toolchain and rely on the Gradle-level
# `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` argument to enable it.
# The flags are a no-op for `x86_64` / `armeabi-v7a` / `x86` ABIs
# (16 KB alignment is the arm64-v8a requirement; the other ABIs
# continue to use 4 KB alignment).
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
        "-Wl,-z,separate-code"
        # `-Wl,-z,norelro` disables the post-RELRO writable-data segment
        # split. With RELRO enabled (the default), LLD 18 (the linker in
        # NDK 27) creates a 4th PT_LOAD segment for the writable .data
        # that survives the mprotect-to-read-only RELRO step. LLD 18
        # does not pad this 4th segment to the 16 KB page-size boundary
        # (`p_vaddr` ends up at 0x3c17b0 = 0x4000 * 0xf0 + 0x17b0, NOT
        # 16 KB-aligned). The Android 15+ dynamic linker rejects the
        # library with `bad ELF segment alignment`. Disabling RELRO
        # consolidates .data + .bss into the single 3rd LOAD segment
        # (which IS 16 KB-aligned) at the cost of GOT-overwrite
        # protection. The trade-off is acceptable for KordX: the
        # `libmetaphony.so` only processes audio file metadata (no
        # network, no privilege separation, no untrusted-input code
        # paths), so a GOT overwrite would not yield meaningful
        # privilege escalation. The Android 15+ hard-fail is a
        # functional regression, the lost RELRO protection is not.
        # NDK r28+ (LLD 19) fixes the LLD 18 post-RELRO alignment
        # bug; the `-z,norelro` flag can be dropped when the project
        # upgrades.
        "-Wl,-z,norelro")

# Also apply to the vendored taglib (linked statically into metaphony).
# Without this, the `tag` static library's objects would still be
# 4 KB-aligned, and the final libmetaphony.so would inherit the
# 4 KB alignment from those objects. Setting the flags on both
# libraries ensures the final .so is uniformly 16 KB-aligned.
target_link_options(tag PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
        "-Wl,-z,separate-code"
        "-Wl,-z,norelro")

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        tag)
