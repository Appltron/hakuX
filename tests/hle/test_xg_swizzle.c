#include "hw/xbox/hle/tex/xg_swizzle.h"
#include "hle_test.h"

static uint8_t src[64 * 64 * 4], dst_fast[64 * 64 * 4], dst_ref[64 * 64 * 4];
static uint8_t back[64 * 64 * 4];

static void fill(uint8_t *p, size_t n, uint32_t seed)
{
    for (size_t i = 0; i < n; i++) {
        seed = seed * 1103515245u + 12345u;
        p[i] = (uint8_t)(seed >> 16);
    }
}

static void test_offsets(void)
{
    HT_EQ_U32(hle_xg_swizzle_offset(4, 4, 1, 1, 0, 0), 1);
    HT_EQ_U32(hle_xg_swizzle_offset(4, 4, 1, 0, 1, 0), 2);
    HT_EQ_U32(hle_xg_swizzle_offset(4, 4, 1, 2, 0, 0), 4);
    HT_EQ_U32(hle_xg_swizzle_offset(4, 4, 1, 0, 2, 0), 8);
    HT_EQ_U32(hle_xg_swizzle_offset(4, 4, 1, 3, 3, 0), 15);
    HT_EQ_U32(hle_xg_swizzle_offset(8, 2, 1, 4, 0, 0), 8);   /* masks x=0xD y=0x2 */
    HT_EQ_U32(hle_xg_swizzle_offset(8, 2, 1, 1, 1, 0), 3);
}

static void check_rect(uint32_t w, uint32_t h, uint32_t bpp, const HleXgRect *r,
                       uint32_t px, uint32_t py)
{
    uint32_t pitch = w * bpp;
    fill(src, sizeof(src), w * 31 + h + bpp);
    memset(dst_fast, 0x5A, sizeof(dst_fast));
    memset(dst_ref, 0x5A, sizeof(dst_ref));
    hle_xg_swizzle_rect(src, pitch, r, dst_fast, w, h, px, py, bpp);
    hle_xg_swizzle_rect_ref(src, pitch, r, dst_ref, w, h, px, py, bpp);
    HT_CHECK(memcmp(dst_fast, dst_ref, (size_t)w * h * bpp) == 0);
    memset(back, 0xA5, sizeof(back));
    hle_xg_unswizzle_rect(dst_fast, w, h, NULL, back, pitch, 0, 0, bpp);
    if (!r && !px && !py) {
        HT_CHECK(memcmp(back, src, (size_t)w * h * bpp) == 0);
    }
}

static void test_rects(void)
{
    check_rect(64, 64, 4, NULL, 0, 0);              /* NEON 4x4 path on arm64 */
    check_rect(32, 32, 2, NULL, 0, 0);
    check_rect(16, 8, 1, NULL, 0, 0);
    check_rect(128, 1, 4, NULL, 0, 0);              /* 1D */
    HleXgRect r = { 8, 4, 16, 8 };
    check_rect(64, 16, 4, &r, 4, 4);                /* sub-rect at a point */
    HleXgRect r4 = { 4, 4, 16, 8 };
    check_rect(64, 64, 4, &r4, 8, 12);              /* aligned sub-rect */
}

static void test_box(void)
{
    uint32_t w = 16, h = 8, d = 4, bpp = 4;
    uint32_t rp = w * bpp, sp = rp * h;
    fill(src, (size_t)sp * d, 77);
    memset(dst_fast, 0, sizeof(dst_fast));
    hle_xg_swizzle_box(src, rp, sp, NULL, dst_fast, w, h, d, 0, 0, 0, bpp);
    uint32_t off = hle_xg_swizzle_offset(w, h, d, 5, 3, 2);
    HT_CHECK(memcmp(dst_fast + off * bpp, src + 2 * sp + 3 * rp + 5 * bpp, bpp) == 0);
    memset(back, 0, sizeof(back));
    hle_xg_unswizzle_box(dst_fast, w, h, d, NULL, back, rp, sp, 0, 0, 0, bpp);
    HT_CHECK(memcmp(back, src, (size_t)sp * d) == 0);
}

int main(void)
{
    HT_RUN(test_offsets);
    HT_RUN(test_rects);
    HT_RUN(test_box);
    HT_DONE();
}
