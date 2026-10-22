# Handoff: hle-native, 2026-10-20

Repository `D:\code\xemu-android-native`, branch `hle-native`. Read
`CLAUDE.md` (device etiquette, build steps; local-only, in
`D:\code\xemu-android`) and `tools/hle/README.md` (tools and debug knobs)
first.

## State of the branch

- Mods and cheats per game: `a40a1ef99d` (library long-press "Mods &
  cheats", repo `mods/<TITLEID>.<VERSION>/`, `mods/README.md`),
  `52e9bcc5e5` (`refresh:` vblank rate, patches past the headers, patches
  over savestates, `debug.hakux.memscan`).
- `88adaf77d5`: a store into vertices a later pass reads waits for the
  decoder (DOA2 z-fight, below).
- `2ad0b1a222`: the MCPX APU runs on the native core's own services in
  `native/dev/apu` (no QEMU layer). `2c67b760c3`: NEON HRTF, batched
  voice-table dirty reports, two voice workers by default.
- Nothing else is uncommitted (the repository's pre-existing file-mode and
  line-ending noise aside, e.g. `native/dev/apu/*`: never stage it).
- `dbdb55cbdf`: vertex positions all move by D3D's 1/32 rounding bias
  instead of each being truncated to 1/16 pixel. Decals flickered against
  the surface under them while the camera moved: Burnout 3's road stains and
  manhole covers, DOA2's eaves shadows (both confirmed fixed by the user;
  `debug.hakux.snap=trunc` for A/B). It replaced the shelved
  `debug.hakux.leqtol` stash, which is dropped (was be0d6a7194).
- DOA2's glow trails: an antialiased 2x2 view of the back buffer's memory
  (its glow pass) became an object of its own, never cleared; it is now the
  back buffer (`pbdraw.c` `linear_size`).

## In progress: DOA2 120 fps mod (`mods/54430006.6/120fps`)

Paused on 2026-10-20 for another task. The user's request: a 120 fps mod
for Dead or Alive 2 Ultimate (`54430006.6`, `doa2.xbe`), made as a mod
(XBE patches; not an emulator feature), the game logic at its normal 60,
the frames in between blended ("interpolate at 120").

**Devices.** 120 Hz checks on the Galaxy Tab S9 (`adb -s
192.168.4.25:40381`, a 120 Hz panel). The game is on its SD card; launch it
with `am start -a android.intent.action.VIEW -d
'content://com.android.externalstorage.documents/tree/5A5D-CCFB%3AROMs%2Fxbox/document/5A5D-CCFB%3AROMs%2Fxbox%2FDead%20or%20Alive%202%20Ultimate%20(USA%2C%20Japan)%20(En%2CJa).iso'
-n com.rfandango.haku_x.debug/com.rfandango.haku_x.LauncherActivity`, then
`setprop debug.hakux.state load:1` after ~30 s. Keep it awake
(`input keyevent KEYCODE_WAKEUP`); when it locks, the user has to unlock
it. The Tab has a 3x render scale override for this game (`per_game_settings`
`54430006.6/render_scale`): at 4x the GPU heats up, the kernel caps it at
401 of 719 MHz (`/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel` 5) and frames
swing between 120 and 52; at 3x it held 120. The RP5 (60 Hz panel) serves
for reverse engineering and logic checks. Savestate slot 1 is the sparring
fight on both.

**What the mod does now** (`tools/mods/doa2_120fps.py <doa2.xbe> <mod.txt>`
writes `mod.txt`; the XBE is
`D:\code\xemu-android\build\hle-xbe\Dead_or_Alive_2_Ultimate__doa2.xbe`):
- `refresh: 120`: the vblank at 120 Hz.
- The game runs as fiber tasks (24 slots at 0xA94570, scheduler 0xFA440,
  current slot 0xA94814). A logic frame is the first main-loop pass of each
  60 Hz tick (D3D's vblank count at `[0x3554F8]+0x1DE8`, halved), decided
  at the controller update (call at 0xFF3AF): only logic frames read the
  controllers. On the other frames the scheduler (0xFA467) runs only the
  tasks that drew (the D3D draws set the slot's bit in DRAWN, 0x10CF0).
- The code sits in the header page (sizeof_headers 0xC68 -> 0x1000):
  variables at 0x10CF0 (DRAWN), 0x10CF4 (PARITY, 0 on logic frames),
  0x10CF8 (LASTTICK); code at 0x10D00-0x10E8F. Free: 0x10E90-0x10FFF.
- Tasks that run every frame without drawing (C_ALWAYS, by entry): the
  select screen's model loader (0x12F490, fed by 0x12F460; skipped, its
  fighters were drawn every other tick) and the fight's render setup
  (slot 12, 0x116B20: resets D3D's render state and the draw lists, sets
  the lights, queues the shadows; skipped, the ground under the fighters
  went darker every other tick). The frame counter 0x87D56C (bumps at
  0xD9364 and 0xDAF32) and the render setup's 0x850730 (0x5C46C) move on
  logic frames only, so a fighter drawn twice in one tick takes the
  game's repeat path (0x5B729).
- Known: the select screen's "A Select / B Back" legend is drawn on logic
  frames only (it flickers at 60 Hz). Not traced yet.
- Finding a task by experiment: a cave that also runs the slots set in a
  guest word (`bt [0x10CFC], slot`), then `setprop debug.hakux.memscan
  poke=10CFC:<mask>,<salt>` on the running game. One build, any number of
  masks.
- Verified on the Tab at 3x: 120 fps, the fighter's frame counter
  (0x8A3918) and the global logic counter (0x87D56C) at 60 a second, inputs
  register. Each logic frame is shown twice: motion still looks like 60.

**Next, in order.**
1. Draw-side updates still run every frame (twice per tick):
   - The game's own "this draw repeats the last frame" flag is the byte
     0x8783C9 (100+ readers). Draw code skips its CPU steps while it is
     set: seam copies (0x4E390), hair and cloth chains (0x591A0), ribbons
     (0x50510). Slot 13 (stage, entry 0x14C610, at 0x14C69F) and slot 14
     (fighters, entry 0x5C3E0, per fighter at 0x5B737) set it while they
     draw when their logic counter hasn't moved. But the stage effects
     (falling petals: a spawn every 5th scene frame at 0x1E4950, the counter
     0xE34780 bumped at 0x1E740A) test 0xE0D7B8, which slot 13 copies from
     0x8783C9 at the top of its loop (0x14C636), before that check. Plan:
     set 0x8783C9 for the whole in-between frame (save it in the controller
     cave on the in-between frame, restore it on the next logic frame).
   - HUD timers the flag doesn't cover: 0x8A29A8 (a fade-in, +1 at
     0x6D60E/0x6D616), 0x88371C (blink, read at 0x6D562), 0xEB8640 (a pulse
     into 0x7BE270 at 0x291900). Bump them on logic frames only.
   - Other counters that rise every frame (`debug.hakux.memscan`
     `snap`/`diff`): 0xBACD60/0xBACDD8 (written at 0xFE437), and ones at
     0xF2AD6C, 0xF3E804, 0xF5CDE8, 0x1056C90 that track D3D's frame count.
     Check what reads them before changing them.
2. Blending (shown half a tick late). Findings so far:
   - Every draw is fixed function with skinning off (`debug.hakux.pbdraws`
     `skin 0`). The fighters are rigid parts, each drawn with WORLD = one
     palette matrix: 0x283EA0 loads it into the current world 0xEA6080 and
     0x288AA0 culls and calls `SetTransform(WORLD, 0xEA6080)` (~460 a
     frame).
   - Palette: 0x875C20 + fighter * 0x800 (32 slots of 0x40: rigid matrices
     or zeros). Inverse palette (world to bone, for seams and ribbons):
     0x87AFA0 + fighter * 0x800. `[0x850504]` and `[0x80B7A0]` point at
     the current fighter's. Both are written once per tick by logic task
     slot 9 (entry 0xE68B0), through the matrix store at 0x283EF0
     (`NC_WATCHPA`).
   - Camera: VIEW 0x879C10, PROJECTION 0x87D360 (set by `SetTransform` at
     0x2F090, 0x26C6C0, 0x26C890, 0x27A690, 0x280720; menus use 0x8A0AE0,
     0x8A0A04). Its writer is not found yet: the sparring camera holds
     still. Watch it (`NC_WATCHPA`) while the fighters move.
   - A run of 890 matrices at 0x855C10-0x863A90 (186 code references)
     changes over time; not identified.
   - Plan: on a logic frame, when the scheduler reaches the first slot
     with a DRAWN bit, save the palettes and the camera, then write the
     halfway values between the last tick's and this tick's. Skip a
     matrix whose translation jumps more than ~1 unit (camera cuts, round
     starts). At the next main-loop pass (the controller cave) put the
     saved ones back, so the in-between frame draws the tick exactly.
     The buffers (~17 KB) come from `MmAllocateContiguousMemory` (kernel
     thunk 0x3C155C, ordinal 165) on first use, the pointer kept in the
     header page. About 500 bytes of code space remain there.
   - To look for afterwards: the seams and the hair roots. The CPU builds
     them from the halfway palette on the logic frame, and the in-between
     frame reuses them while drawing the exact palette. If that shows, run
     the seam steps on in-between frames too (they keep no state), but
     not the hair simulation.
3. Run it on the Tab (the user judges the smoothness), then drop
   "(in progress)" from the mod's name and description.

**Tools for this** (`tools/hle/README.md`): `debug.hakux.memscan`
(`snap`/`diff` for counters, `mats`/`mats1[:va]` for matrix runs and
physical addresses), `NC_WATCHPA=pa:len:delay` (who stores there; the clock
is checked every 4096 rearms, so use a delay of 30 s or less and read
the log ~40 s later), `HAKUX_HLE_PROBE` with `debug.hakux.probe <new>` to
re-arm, `debug.hakux.vpeek va:256` (at most 256 words), `tools/hle/re/`
`cgraph.py` (call graph and trees) and `callargs.py` (a function's call
sites with their pushed arguments).

## Done: Dead or Alive 2 portrait z-fighting (`88adaf77d5`)

- The flicker was the fishnet under the collar (and the hair): two
  identical translucent passes, the second LEQUAL over the first, from
  vertices the title re-skins on the CPU (NOOVERWRITE lock) while the
  decoder was most of a frame behind. A guest store into vertices a later
  pass reads now waits for the decoder (`catch-up:` line;
  `HAKUX_HLE_PB_CATCHUP=0` for A/B). Details in the commit and the README's
  "Pixels that flash" recipe.
- Not run on other titles: PBD titles that draw CPU-rewritten vertices
  twice a frame now make the guest wait for the decoder; watch their fps.

## Not yet run on the native APU

Titles at risk, to run when the user asks: Fable (movies), Halo / PGR2
(DirectSound waits), Burnout 3. Forza is verified (savestate slot 3 on the
RP5 is the race start, used for no-input CPU benches).
