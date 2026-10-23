# Changelog

## v1.0.0

A new core replaces xemu's QEMU-based one.

### New core
- **CPU** — the Xbox's x86 code runs on a FEX JIT (FEXCore fork), and the Xbox kernel is reimplemented natively (`native/kernel`): no QEMU
- **GPU** — the NV2A push buffer is decoded into Vulkan (HLE, `hw/xbox/hle`). Custom drivers (Turnip) load through adrenotools; driver-specific workarounds are quirks (`hw/xbox/hle/vk/quirks.c`, `debug.hakux.quirks`)
- **Audio** — the MCPX APU runs on the core's own services (`native/dev/apu`), out through AAudio
- **Savestates** — per game, in slots, with the hard disk's files
- **Mods and cheats** — per game, from `mods/` (library long-press "Mods & cheats"): XBE patches and replaced disc files; 60 and 120 fps mods for 13 games (`mods/README.md`, generators in `tools/mods`)
- **Controllers** — each physical controller routed to a player with its own mapping; the on-screen controller adds to player 1

### New features
- **Updates** — hakuX looks for a newer release on GitHub when it starts and on demand (Settings → Updates); one found shows its notes and installs from there
- **Frontends** — a game launched from ES-DE or another frontend returns to it when it closes
- **Console clock** — follows the phone's time and time zone, or a time set by hand (Settings → Console → Clock)
- **Start errors** — a game that can't start says why (the file can't be read, isn't an Xbox disc, or its program can't load) and goes back to the library
- **Mods** — Halo 2's 60 fps mod draws characters, vehicles and objects at 60; DOA2's 120 fps mod keeps the select screen's legend

### Performance
- **No pipeline-cache stutter** — each game keeps its own pipeline cache, written only after new compiles; the one shared by every game was rewritten every few seconds and froze the picture for 0.2–0.8 s (Ninja Gaiden Black)
- **Audio** — about 32 ms of latency instead of 128 ms, and less CPU for the sound engine
- **GPU emulation** — faster push-buffer decoding, texture unswizzling (NEON) and hashing, fewer lookups per draw
- **Savestates** — compressed on several threads (a Halo 2 save: 9.6 s to 3.9 s)
- **Idle frames** — the screen isn't redrawn when nothing changed, and the on-screen controller redraws only what changed
- **Disc reads** — a large read waits only its own thread, not the whole game
- **Library** — folders scanned in one pass (38 games: 270 ms to 63 ms)

### App
- **On-screen controller** — the menu button is a green dot
- **Large screens** — Android 16 keeps the landscape lock (the app is declared a game)
- **Storage** — JIT symbol maps left by earlier builds (gigabytes on some devices) are deleted at start
- **Backups** — hakuX's data is left out of Android's cloud backups and device transfers
- **Library** — savestate thumbnails and driver ZIPs load off the main thread

## v0.3.1

### New Features
- **Persistent log capture** — logs kept per session, rotated

### Improvements
- **Texture decompression** — S3TC decoded on several threads; 3D textures no longer use native BC (it corrupted BC3)
- **Surface eviction** — lazy, without a GPU finish per eviction, and no download for VRAM data that is never read
- **Texture cache** — no thrashing from an aggressive memory budget trim
- **Profiling** — all instrumentation behind `NV2A_PERF_LOG` (off by default)
- **XISO conversion** — extract-xiso replaces the Rust xdvdfs converter (no Rust/Cargo dependency)

## v0.3.0

### New Features
- **Per-game settings** — overrides per game in the settings UI
- **Xbox dashboard** — dashboard management, HDD tools, Insignia networking (NAT)
- **Texture dump and replacement**
- **Game compatibility quirks** — a per-game layer (Fable's cycle-breaker)
- **Debug log export** — from the settings
- **Skip boot animation** toggle

### Improvements
- **Vulkan** — native BC texture upload when the GPU has it, batched render sync, NEON blits, descriptor management without bindless textures
- **Vertex shaders** — the emulator stub replaced; uniform uploads optimized
- **Audio** — aligned with x1_box
- **Emulator process** — runs separately again (`:xemu`)
- **XISO** — the original ISO kept after conversion

### Bug Fixes
- Surface eviction VRAM corruption and stale texture sampling
- Vulkan pipeline exhaustion and eviction crashes
- A crash on a quick emulation restart (RCU)
- The 30 fps trap
- XISO conversion launching the original file when SAF renamed the output

## v0.2.1

### Bug Fixes
- **Fix HDD corruption** — restore block device flush on app terminate and background to persist QCOW2 metadata. Cache partition clear now uses synchronous writes
- **Fix game freeze on TB flush** — disable post-flush rewarm on Android which could hang on self-modified code
- **Fix TB cache prewarm crash** — disable startup prewarm on Android. Add build hash to tb_cache.bin for auto-invalidation on new builds
- **Fix on-screen controller hidden on some devices** — filter out virtual/internal devices (accelerometers, gyroscopes) that falsely report as gamepads

### Improvements
- **Simplified FPS overlay** — shows only FPS count
- **Unified log tags** — all logcat output uses hakuX prefix. Filter with: `adb logcat | grep hakuX`
- **HDD cache clear on background thread** — prevents ANR on large images

## v0.2.0

### New Features
- **OpenGL ES renderer** — experimental alternative to Vulkan, selectable in Graphics settings
- **EEPROM editor** — change Xbox language, video standard, resolution modes, aspect ratio, refresh rate
- **Settings index** — reorganized into 4 sections: hakuX Data, Graphics, Debug, EEPROM
- **System file pickers** — change MCPX ROM, Flash ROM, HDD image from settings
- **HDD export** — save a copy of the Xbox HDD to any folder
- **Games folder in settings** — moved from library screen to settings
- **Game search** — search bar with live filtering in game library
- **Pull-to-refresh** — swipe down to rescan games folder
- **XISO progress bar** — real progress tracking across copy/convert/save phases
- **XISO integrity check** — verifies XISO before launch, auto-rebuilds from ISO if corrupt

### Improvements
- **Controller overlay** — D-pad/stick repositioned, LS/RS as separate buttons, menu button, swipe-up gesture, trigger axis fix (-1.0 for release), per-pointer tracking
- **Pause menu** — actually pauses emulation, simplified UI
- **NEON optimizations** — S3TC decompression and texture swizzle (benefits both renderers)
- **XISO conversion** — staged in temp directory, clean up on interruption, write permission check
- **FPS overlay** — shows driver info or "OpenGL ES" label, frame pacing, shader stats
- **Clear code cache** button in Debug settings
- **Graceful VK texture LRU handling** — skip instead of crash when cache exhausted

### Bug Fixes
- Fix TB cache crash when FP settings change between sessions
- Fix pause/resume not working from in-game menu
- Fix GLES geometry shader crash (gl_PointSize unavailable without extension)
- Fix GLES shader version detection (was hardcoded to 320, now auto-detected)
- Fix shader link failure crash (graceful fallback instead of assert)
- Fix shared GLSL changes breaking VK (gated behind renderer check)

### Rebranding
- Package renamed to `com.rfandango.haku_x`
- Debug builds install alongside release (`com.rfandango.haku_x.debug`)

## v0.1.0

- Initial release
