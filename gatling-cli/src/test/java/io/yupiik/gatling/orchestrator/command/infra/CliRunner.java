package io.yupiik.gatling.orchestrator.command.infra;

import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.gatling.controller.infra.JUnitModule;
import io.yupiik.gatling.controller.infra.Kubernetes;
import java.util.List;
import java.util.stream.Stream;

public final class CliRunner {
    private CliRunner() {
        // no-op
    }

    public static void cli(final Kubernetes kubernetes, final String... args) {
        try (final var container = ConfiguringContainer.of()
                        .register(new JUnitModule(kubernetes))
                        .register(new ProvidedInstanceBean<>(
                                DefaultScoped.class, ConfigurationSource.class, () -> new ConfigurationSource() {
                                    @Override
                                    public String get(final String key) {
                                        return switch (key) {
                                            case "kubernetes.master" -> kubernetes.base();
                                            case "kubernetes.token" -> "junit";
                                            default -> null;
                                        };
                                    }
                                }))
                        .register(new FusionModule() {
                            @Override
                            public Stream<FusionBean<?>> beans() {
                                return Stream.of(new ProvidedInstanceBean<>(
                                        DefaultScoped.class, Args.class, () -> new Args(List.of(args))));
                            }
                        })
                        .start();
                final var runner = container.lookup(CliAwaiter.class)) {
            runner.instance().await();
        }
    }

    public static void cli(final String... args) {
        try (final var container = ConfiguringContainer.of()
                        .register(new FusionModule() {
                            @Override
                            public Stream<FusionBean<?>> beans() {
                                return Stream.of(new ProvidedInstanceBean<>(
                                        DefaultScoped.class, Args.class, () -> new Args(List.of(args))));
                            }
                        })
                        .start();
                final var runner = container.lookup(CliAwaiter.class)) {
            runner.instance().await();
        }
    }
}
