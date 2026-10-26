/*
 * hakuX native core — the NAT backend (net=nat): the Internet through a
 * user-mode TCP/IP stack (libslirp). Not in this build yet: the mode starts
 * without a link.
 */
#include "native/net/net.h"
#include "native/core/nc.h"

static bool nat_init(void)
{
    nc_log("hakuX-native-net", "nat: not in this build");
    return false;
}

static void nat_input(const uint8_t *frame, size_t len)
{
}

static int nat_fill(struct pollfd *fds, int max, int *timeout_ms)
{
    return 0;
}

static void nat_poll(struct pollfd *fds, int n)
{
}

const NcNetBackend nc_net_nat = {
    .name = "nat",
    .init = nat_init,
    .input = nat_input,
    .fill = nat_fill,
    .poll = nat_poll,
};
