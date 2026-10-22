# Vulkan layers (debug builds)

Libraries here go into debug APKs only, beside the core in the app's native
library directory. With the system Vulkan driver, Android loads the layers a
debuggable app asks for through its GPU debug-layer settings. A custom driver
(Turnip, through adrenotools) bypasses Android's loader and those settings:
the core puts the validation layer in front of it itself
(`native/app/vklayer.c`) when `debug.hakux.vkvalidation` is 1 at launch.

- `libVkLayer_khronos_validation.so`: the Khronos validation layer (tracked).
- `libVkLayer_GLES_RenderDoc.so`: RenderDoc's Android capture layer, not
  tracked (~21 MB, see the repository's `.gitignore`). To capture with
  RenderDoc, drop it in from a RenderDoc Android package of the same
  version as the qrenderdoc you attach with (`lib/arm64-v8a/`).

## Turning a layer on

```
adb shell settings put global enable_gpu_debug_layers 1
adb shell settings put global gpu_debug_app com.rfandango.haku_x.debug
adb shell settings put global gpu_debug_layers VK_LAYER_KHRONOS_validation
```

For RenderDoc, `gpu_debug_layers` is `VK_LAYER_RENDERDOC_Capture`; several
layers are separated by `:`. The next game start loads them. Validation
messages go to logcat (tag `VALIDATION`).

With the custom driver (what the settings install):

```
adb shell setprop debug.hakux.vkvalidation 1
```

The next game start loads it; `0` or empty turns it off. Turnip keeps the
first word of its dispatchable objects for itself, so the core never writes
a loader dispatch key there: one instance and one device per run, which the
layer's separate instance and device maps take.

## Turning it off

```
adb shell settings put global enable_gpu_debug_layers 0
```
