package io.yupiik.gatling.orchestrator.command.kubernetes;

import static java.time.Clock.systemUTC;
import static java.util.logging.Level.FINEST;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClient;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClientConfiguration;
import io.yupiik.fusion.httpclient.core.listener.impl.ExchangeLogger;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

@DefaultScoped
public class KubernetesClientFactory {
    @Bean
    @ApplicationScoped // wrapped to enable proper cleanup on shutdown
    public ScheduledExecutorService threads() {
        return Executors.newScheduledThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                Thread.ofVirtual()
                        .name("io.yupiik.fusion.kubernetes.operator-", 0)
                        .factory());
    }

    @Bean
    @ApplicationScoped
    public KubernetesClient kubernetesClient(
            final KubernetesConfiguration configuration, final ScheduledExecutorService executor) {
        return new KubernetesClient(new io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration()
                .setClientCustomizer(b -> b.connectTimeout(Duration.ofMinutes(configuration.timeout()))
                        .executor(executor))
                .setClientWrapper(client -> {
                    final var logger = Logger.getLogger(KubernetesClient.class.getName());
                    return new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                            .setDelegate(client)
                            .setRequestListeners(
                                    List.of(new ExchangeLogger(logger, systemUTC(), logger.isLoggable(FINEST)))));
                })
                .setMaster(configuration.master())
                .setToken(configuration.token())
                .setCertificates(configuration.certificates())
                .setSkipTls(configuration.skipTls()));
    }
}
