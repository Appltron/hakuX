/*
 * hakuX native core — the APU's output: 48 kHz 16-bit stereo on AAudio.
 *
 * The frame thread pushes each EP frame (256 samples, 1024 bytes) into a
 * ring; AAudio's data callback drains it. One producer and one consumer, so
 * the ring is two counters: the producer publishes its writes with the head
 * (release), the callback its reads with the tail. If no stream opens, a
 * thread drains the ring at the device's rate instead (the sound goes
 * nowhere, the APU keeps its timing).
 */
#include "native/dev/apu/apu_int.h"

#include <aaudio/AAudio.h>

#define OUT_RATE 48000
#define OUT_FRAME_BYTES 4   /* a stereo sample: two 16-bit channels */

static struct {
    uint8_t *buf;
    uint32_t cap;
    uint64_t head, tail;     /* bytes written, bytes read */
    int samples;             /* the callback's size, in frames */
    pthread_cond_t *wake;
    AAudioStream *stream;
    bool started, app_paused;
    uint32_t callbacks, short_callbacks;
} g_out;

int apu_out_queued(void)
{
    return (int)(apu_ld_acq(&g_out.head) - apu_ld_acq(&g_out.tail));
}

void apu_out_push(const void *frame, int bytes)
{
    const uint64_t head = g_out.head;
    assert(head + (uint64_t)bytes - apu_ld_acq(&g_out.tail) <= g_out.cap);
    const uint32_t at = (uint32_t)(head % g_out.cap);
    const uint32_t first = MIN((uint32_t)bytes, g_out.cap - at);
    memcpy(g_out.buf + at, frame, first);
    memcpy(g_out.buf, (const uint8_t *)frame + first, (uint32_t)bytes - first);
    apu_st_rel(&g_out.head, head + (uint64_t)bytes);
}

static int pop(uint8_t *out, int bytes)
{
    const uint64_t tail = g_out.tail;
    const int n = (int)MIN((uint64_t)bytes, apu_ld_acq(&g_out.head) - tail);
    const uint32_t at = (uint32_t)(tail % g_out.cap);
    const uint32_t first = MIN((uint32_t)n, g_out.cap - at);
    memcpy(out, g_out.buf + at, first);
    memcpy(out + first, g_out.buf, (uint32_t)n - first);
    apu_st_rel(&g_out.tail, tail + (uint64_t)n);
    return n;
}

void apu_out_stats(uint32_t *callbacks, uint32_t *short_callbacks)
{
    *callbacks = apu_xchg(&g_out.callbacks, 0);
    *short_callbacks = apu_xchg(&g_out.short_callbacks, 0);
}

/* One callback's worth: what the ring has, waiting up to 5 ms (in 0.5 ms
 * steps, waking the frame thread each time) for a whole callback; the rest
 * silence. Paused (nc_pause_set), silence, the ring kept: the frame thread
 * stops at its high watermark. */
static void fill(uint8_t *stream, int bytes)
{
    if (nc_paused()) {
        memset(stream, 0, (size_t)bytes);
        return;
    }
    for (int i = 0; i < 10 && apu_out_queued() < bytes; i++) {
        usleep(500);
        pthread_cond_broadcast(g_out.wake);
        if (nc_paused()) {
            memset(stream, 0, (size_t)bytes);
            return;
        }
    }
    const int n = pop(stream, bytes);
    apu_inc(&g_out.callbacks);
    if (n < bytes) {
        apu_inc(&g_out.short_callbacks);
        memset(stream + n, 0, (size_t)(bytes - n));
    }
    pthread_cond_broadcast(g_out.wake);
}

static aaudio_data_callback_result_t on_data(AAudioStream *s, void *user, void *data, int32_t frames)
{
    fill(data, frames * OUT_FRAME_BYTES);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void *null_sink(void *arg)
{
    static uint8_t buf[16384];
    const int len = MIN(g_out.samples * OUT_FRAME_BYTES, (int)sizeof(buf));
    const long period_ns = (long)((int64_t)(len / OUT_FRAME_BYTES) * 1000000000LL / OUT_RATE);
    struct timespec next;
    clock_gettime(CLOCK_MONOTONIC, &next);
    for (;;) {
        next.tv_nsec += period_ns;
        while (next.tv_nsec >= 1000000000L) {
            next.tv_nsec -= 1000000000L;
            next.tv_sec++;
        }
        clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &next, NULL);
        fill(buf, len);
    }
    return NULL;
}

int apu_out_open(int capacity_bytes, int samples, pthread_cond_t *wake)
{
    g_out.buf = calloc(1, (size_t)capacity_bytes);
    g_out.cap = (uint32_t)capacity_bytes;
    g_out.samples = samples;
    g_out.wake = wake;

    AAudioStreamBuilder *b = NULL;
    aaudio_result_t r = AAudio_createStreamBuilder(&b);
    if (r == AAUDIO_OK) {
        AAudioStreamBuilder_setSampleRate(b, OUT_RATE);
        AAudioStreamBuilder_setChannelCount(b, 2);
        AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setFramesPerDataCallback(b, samples);
        AAudioStreamBuilder_setDataCallback(b, on_data, NULL);
        r = AAudioStreamBuilder_openStream(b, &g_out.stream);
        AAudioStreamBuilder_delete(b);
        if (r != AAUDIO_OK) {
            g_out.stream = NULL;
        }
    }
    g_out.started = true;
    if (g_out.stream) {
        if (!g_out.app_paused) {
            AAudioStream_requestStart(g_out.stream);
        }
    } else {
        nc_log("hakuX-apu", "no output stream (AAudio: %s); draining without sound",
               AAudio_convertResultToText(r));
        pthread_t t;
        apu_thread_start(&t, "apu.null", null_sink, NULL, false);
    }
    return samples * OUT_FRAME_BYTES;
}

/* The emulation paused with the activity (app_main.c): the stream stops
 * pulling silence meanwhile, and starts again on resuming. */
void nc_audio_set_paused(bool on)
{
    g_out.app_paused = on;
    if (g_out.stream && g_out.started) {
        if (on) {
            AAudioStream_requestPause(g_out.stream);
        } else {
            AAudioStream_requestStart(g_out.stream);
        }
    }
}
