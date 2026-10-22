# Vulkan layers (debug builds)

Libraries here go into debug APKs only, beside the core in the app's native
library directory. hakuX never enables a layer itself: Android loads the
layers a debuggable app asks for through its GPU debug-layer settings.

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

## Turning it off

```
adb shell settings put global enable_gpu_debug_layers 0
```
