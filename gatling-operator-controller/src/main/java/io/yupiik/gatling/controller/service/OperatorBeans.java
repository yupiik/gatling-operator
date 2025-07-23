package io.yupiik.gatling.controller.service;

import static java.time.Clock.systemUTC;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import java.time.Clock;

@ApplicationScoped
public class OperatorBeans {
    @Bean
    @ApplicationScoped
    public Clock clock() {
        return systemUTC();
    }
}
