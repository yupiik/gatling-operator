package io.yupiik.gatling.controller.infra;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class ConstantClock extends Clock {
    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.EPOCH;
    }
}
