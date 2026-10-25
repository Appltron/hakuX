/*
 * hakuX native core — a custom Vulkan driver for the HLE GPU (Turnip),
 * through adrenotools: the app's GPU driver setting ("vkdriver=" from
 * NativeCoreActivity).
 */
#include "native/app/app.h"
#include "native/hle/hle_native.h"

#ifdef NC_ADRENOTOOLS
#include <adrenotools/driver.h>
#include <dlfcn.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/system_properties.h>

void nc_vk_driver_load(const char *spec)
{
#ifdef NC_ADRENOTOOLS
    /* Turnip's debug flags for A/B runs (debug.hakux.tu_debug, e.g.
     * "sysmem"): read when the driver creates its device. */
    char tu[PROP_VALUE_MAX] = "";
    __system_property_get("debug.hakux.tu_debug", tu);
    if (tu[0]) {
        setenv("TU_DEBUG", tu, 1);
        nc_log("hakuX-app", "TU_DEBUG=%s", tu);
    }
    char dir[1024];
    snprintf(dir, sizeof(dir), "%s", spec);
    char *lib = strchr(dir, '|');
    char *hooks = lib ? strchr(lib + 1, '|') : NULL;
    if (lib && hooks) {
        *lib++ = 0;
        *hooks++ = 0;
        void *h = adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, NULL, hooks, dir, lib, NULL, NULL);
        void *gipa = h ? dlsym(h, "vkGetInstanceProcAddr") : NULL;
        if (gipa) {
            nc_hle_set_vk_loader(nc_vk_layers(gipa));
        }
        nc_log("hakuX-app", "Vulkan driver %s from %s: %s", lib, dir,
               gipa ? "loaded" : "failed, the system driver it is");
    }
#else
    nc_log("hakuX-app", "Vulkan driver requested, but this build has no adrenotools");
#endif
}
