# Patch 2 — Dual-display viewport editor

Move and resize **both the internal and external display viewports** from the Odin touchscreen.

- Select Internal or External in **Edit screen viewports**.
- Apply **4:3 internally** or **15:9 externally**; keep the current width where possible, fitting the frame if necessary.
- Independent saved placements, with landscape and portrait profiles; existing internal placements are retained.
- Save both displays or cancel both edits. The external display previews changes live.

## Tested setup

Built and installed by the maintainer on an **AYN Odin 3 with an external display**. On 2026-10-27, the maintainer reported successful testing of editing, aspect presets, persistence, cancellation, and internal touch alignment. Other devices and reconnect/rotation edge cases are not certified.

## APK and source

Asset: `app-vanilla-relWithDebInfo.apk` (Vanilla / RelWithDebInfo; arm64-v8a and x86_64).

The attached APK is the maintainer-supplied, tested build, not a newly rebuilt binary. Its implementation is based on commit [d046f845](https://github.com/ZeroNoir51/azahar-zn51/commit/d046f845b41bbf4127eb91f43a67e8732621a806), with the single-job CMake configuration used locally now included in this release's source. Documentation was updated after testing.

Clone the repository at the release tag with submodules (`git clone --recurse-submodules --branch patch-2 https://github.com/ZeroNoir51/azahar-zn51.git`). Use Java 17, Android SDK 35 and NDK 27.3.13750724, then run from `src/android`:

```sh
./gradlew assembleVanillaRelWithDebInfo --max-workers=1 --no-parallel
```

GitHub's automatic source archives do not bundle submodule contents; use the recursive clone for dependencies. Build timestamps and local signing keys can make rebuilt APK bytes differ.

SHA-256:
```text
57968632022b24f7835e292a24b6e1c31d844841d3b48da5210cb1a6687192af  app-vanilla-relWithDebInfo.apk
```

## Scope and license

Presets transform the whole rendering surface, including letterboxing; they do not crop black bars or independently rearrange native 3DS screens.

Unofficial experimental fork for personal Odin 3 use, open to everyone under GPL-3.0-or-later. Original Azahar/Citra and dependency notices remain applicable. Fork changes were developed with assistance from **OpenAI GPT-6 Astra**. No endorsement by Azahar or OpenAI; provided without warranty.
