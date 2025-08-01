package io.yupiik.gatling.orchestrator.command;

import static io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus.BenchmarkStatus.FAILED;
import static io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus.BenchmarkStatus.FINISHED;
import static io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus.BenchmarkStatus.RUNNING;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.gatling.controller.bundlebee.BundleBeeService;
import io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkSpec;
import io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

@DefaultScoped
@Command(name = "bench", description = "Run a benchmark based on the provided configuration.")
public class BenchmarkCommand implements Runnable {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final BenchmarkCommandConfiguration configuration;
    private final JsonMapper json;
    private final String name;
    private final KubernetesClient kubernetes;
    private final ScheduledExecutorService executor;
    private final BundleBeeService bundleBee;

    public BenchmarkCommand(
            final BenchmarkCommandConfiguration configuration,
            final KubernetesClient client,
            final ScheduledExecutorService executorService,
            final BundleBeeService bundleBee,
            final JsonMapper json) {
        this.configuration = configuration;
        this.json = json;
        this.kubernetes = client;
        this.executor = executorService;
        this.bundleBee = bundleBee;
        this.name = configuration.baseUri().substring(configuration.baseUri().lastIndexOf('/') + 1);
    }

    @Override
    public void run() {
        try {
            doRun(configuration).get(configuration.spec().timeout(), MILLISECONDS);
        } catch (final RuntimeException | InterruptedException | ExecutionException | TimeoutException re) {
            logger.log(SEVERE, re, () -> "Can't execute spec properly: " + configuration.spec());
            throw re instanceof RuntimeException e ? e : new IllegalStateException(re);
        }
    }

    private CompletionStage<?> doExecutePipeline(
            final long globalTimeout,
            final Iterator<Map.Entry<Integer, List<GatlingBenchmarkSpec.Alveolus>>> iterator) {
        if (!iterator.hasNext()) {
            return completedFuture(null);
        }

        final var range = iterator.next();
        logger.info(() -> "Running range: #" + range.getKey() + " (#" + range.getValue() + " jobs)");

        final var index = new AtomicInteger();
        final var baseImplicitPlaceholders = Map.of(
                "gatling-operator.implicit.version",
                VersionHolder.VERSION,
                "gatling-operator.implicit.orchestrator-ip",
                ofNullable(System.getenv("K8S_POD_IP")).orElse("localhost"),
                "gatling-operator.implicit.parent-name",
                name,
                "gatling-operator.implicit.range",
                Integer.toString(range.getKey()),
                // encourage cleanup - otherwise to setup manually
                "generic-job.labels",
                "{\"gatling.yupiik.io/parent-name\":\"" + name + "\"}");
        return setStatus(new GatlingBenchmarkStatus(RUNNING, null, range.getKey()))
                .thenComposeAsync(
                        i -> allOf(range.getValue().stream()
                                .map(it -> {
                                    final var indexValue = Integer.toString(index.getAndIncrement());
                                    return bundleBee
                                            .deploy(
                                                    it.name(),
                                                    it.timeout(),
                                                    merge(
                                                            baseImplicitPlaceholders,
                                                            Map.of(
                                                                    "gatling-operator.implicit.index",
                                                                    indexValue,
                                                                    // defaults, can be overriden by custom placeholders
                                                                    "generic-job.name",
                                                                    name + "-" + range.getKey() + "-" + indexValue,
                                                                    "generic-job.activeDeadlineSeconds",
                                                                    Long.toString(
                                                                            Math.max(
                                                                                            60_000,
                                                                                            Math.max(
                                                                                                    it.timeout(),
                                                                                                    globalTimeout))
                                                                                    / 1_000),
                                                                    // do not give perms to the job until it is explicit
                                                                    // -
                                                                    // it.placeholdlers()
                                                                    "generic-job.serviceAccountName",
                                                                    "default"),
                                                            it.placeholders()))
                                            .toCompletableFuture();
                                })
                                .toArray(CompletableFuture<?>[]::new)),
                        executor)
                .thenComposeAsync(done -> doExecutePipeline(globalTimeout, iterator), executor);
    }

    @SafeVarargs
    private Map<String, String> merge(final Map<String, String>... maps) {
        return Stream.of(maps)
                .filter(Objects::nonNull)
                .flatMap(it -> it.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
    }

    private CompletionStage<?> executePipeline(final BenchmarkCommandConfiguration configuration) {
        final var byRange = configuration.spec().pipeline().stream()
                .collect(groupingBy(GatlingBenchmarkSpec.Alveolus::range))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        final var alveoli = byRange.stream()
                .flatMap(it -> it.getValue().stream())
                .map(GatlingBenchmarkSpec.Alveolus::name)
                .collect(toSet());
        final var resolvedAlveoli = allOf(alveoli.stream()
                .map(bundleBee::resolve)
                .map(it -> it.thenRun(() -> {}).toCompletableFuture())
                .toArray(CompletableFuture<?>[]::new));
        return resolvedAlveoli.thenComposeAsync(
                ignored -> doExecutePipeline(configuration.spec().timeout(), byRange.iterator()), executor);
    }

    private CompletableFuture<Void> doRun(final BenchmarkCommandConfiguration configuration) {
        return setStatus(new GatlingBenchmarkStatus(RUNNING, null, -1))
                .thenComposeAsync(
                        ignored -> executePipeline(configuration)
                                .thenApply(it -> (Throwable) null)
                                .exceptionally(e -> e),
                        executor)
                // async finally
                .thenComposeAsync(
                        error -> {
                            if (error != null) {
                                logger.log(SEVERE, error, error::getMessage);
                            }
                            if (configuration.spec().autoClean()) {
                                return delete();
                            }
                            if (error != null) {
                                final var unwrapped = error instanceof CompletionException ce ? ce.getCause() : error;
                                return setStatus(new GatlingBenchmarkStatus(
                                        FAILED,
                                        new GatlingBenchmarkStatus.Message("An error occurred", error.getMessage()),
                                        unwrapped instanceof PipelineException pe ? pe.range : -1));
                            }
                            return setStatus(new GatlingBenchmarkStatus(FINISHED, null, -1));
                        },
                        executor)
                .toCompletableFuture();
    }

    private CompletionStage<Void> delete() {
        return kubernetes
                .sendAsync(
                        HttpRequest.newBuilder()
                                .method(
                                        "DELETE",
                                        HttpRequest.BodyPublishers.ofString(
                                                "{\"kind\":\"DeleteOptions\",\"apiVersion\":\"v1\",\"propagationPolicy\":\"Foreground\",\"gracePeriodSeconds\":0}",
                                                UTF_8))
                                .uri(URI.create(configuration.baseUri()))
                                .header("content-type", "application/json")
                                .header("accept", "application/json")
                                .build(),
                        ofString())
                .thenAcceptAsync(
                        res -> {
                            if (res.statusCode() > 299) {
                                logger.warning(() -> "Can't delete benchmark '" + name + "': " + res.body());
                                throw new IllegalStateException("Can't delete benchmark '" + name + "'");
                            }
                        },
                        executor);
    }

    private CompletionStage<Void> setStatus(final GatlingBenchmarkStatus status) {
        return kubernetes
                .sendAsync(
                        HttpRequest.newBuilder()
                                .method(
                                        "PATCH",
                                        HttpRequest.BodyPublishers.ofString(
                                                "{\"status\":" + json.toString(status) + "}", UTF_8))
                                .uri(URI.create(configuration.baseUri() + "/status"))
                                .header("content-type", "application/merge-patch+json")
                                .build(),
                        ofString())
                .thenAcceptAsync(
                        res -> {
                            if (res.statusCode() > 299) {
                                logger.warning(() -> "Can't update status of benchmark '" + name + "': " + res.body());
                            }
                        },
                        executor);
    }

    private static class PipelineException extends RuntimeException {
        private final int range;

        public PipelineException(final int range, final String message, final Throwable cause) {
            super(message, cause);
            this.range = range;
        }
    }
}
