package io.yupiik.gatling.controller.listener;

import static io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus.BenchmarkStatus.FAILED;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.function.Function.identity;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toMap;

import io.yupiik.fusion.framework.api.main.Launcher;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.kubernetes.crd.CustomResourceDefinition;
import io.yupiik.fusion.framework.build.api.kubernetes.crd.CustomResourceDefinition.PrinterColumn;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.gatling.controller.bundlebee.BundleBeeService;
import io.yupiik.gatling.controller.configuration.GatlingOperatorConfiguration;
import io.yupiik.gatling.controller.model.GatlingBenchmark;
import io.yupiik.gatling.controller.model.GenericItems;
import io.yupiik.gatling.controller.version.VersionHolder;
import io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkSpec;
import io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus;
import io.yupiik.kubernetes.operator.base.spi.Operator;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

@CustomResourceDefinition(
        /* defaults
        namespaced = true,
        version = GatlingBenchmarkOperator.VERSION,
         */
        group = GatlingBenchmarkOperator.GROUP,
        name = GatlingBenchmarkOperator.NAME,
        shortNames = {"gb"},
        spec = GatlingBenchmarkSpec.class,
        status = GatlingBenchmarkStatus.class,
        additionalPrinterColumns = {@PrinterColumn(name = "Status", type = "string", jsonPath = ".status.status")},
        selectableFields = {
            ".status.status",
        },
        description =
                """
                        An operator which will trigger Gatling instances and optionally post-process the reports/simulations.
                        """)
@ApplicationScoped
public class GatlingBenchmarkOperator extends Operator.Base<GatlingBenchmark> {
    static final String GROUP = "gatling.yupiik.io";
    static final String VERSION = "v1";
    static final String NAME = "GatlingBenchmark";

    private static final String CRD_LABEL = GROUP + "/parent-name";
    private static final String START_LABEL = GROUP + "/started-timestamp";

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .appendValue(HOUR_OF_DAY, 2)
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendValue(SECOND_OF_MINUTE, 2)
            .toFormatter();

    private final ScheduledExecutorService scheduledExecutorService;
    private final KubernetesClient kubernetes;
    private final JsonMapper json;
    private final String baseJobsUri;
    private final String baseServicesUri;
    private final String baseUri;
    private final BundleBeeService deployer;
    private final Clock clock;
    private final GatlingOperatorConfiguration configuration;

    // simple and likely sufficient (even if not 100% accurate)
    // way to avoid to delete too early or to exit in the middle of a create
    // todo: rework that part
    private final Map<String, CompletionStage<?>> pending = new ConcurrentHashMap<>();

    // for subclassing/proxy
    protected GatlingBenchmarkOperator() {
        super(null, null);
        this.scheduledExecutorService = null;
        this.kubernetes = null;
        this.json = null;
        this.baseJobsUri = null;
        this.baseServicesUri = null;
        this.baseUri = null;
        this.deployer = null;
        this.clock = null;
        this.configuration = null;
    }

    public GatlingBenchmarkOperator(
            final KubernetesClient client,
            final BundleBeeService deployer,
            final JsonMapper json,
            final Clock clock,
            final GatlingOperatorConfiguration configuration,
            final ScheduledExecutorService executorService) {
        super(
                GatlingBenchmark.class,
                new DefaultOperatorConfiguration(
                        true,
                        List.of(client.namespace().orElse("default")),
                        NAME.toLowerCase(Locale.ROOT) + "s",
                        GROUP + "/" + VERSION));
        this.clock = clock;
        this.kubernetes = client;
        this.json = json;
        this.deployer = deployer;
        this.scheduledExecutorService = executorService;
        this.configuration = configuration;

        final var namespace = client.namespace().orElse("default");
        this.baseJobsUri = "https://kubernetes.api/apis/batch/v1/namespaces/" + namespace + "/jobs";
        this.baseServicesUri = "https://kubernetes.api/api/v1/namespaces/" + namespace + "/services";
        this.baseUri = "https://kubernetes.api/apis/" + GROUP + '/' + VERSION + "/namespaces/" + namespace + "/"
                + NAME.toLowerCase(ROOT) + 's';
    }

    @Override
    public CompletionStage<?> onStart() {
        logger.info("Starting watching gatling benchmarks descriptors");
        return super.onStart();
    }

    @Override
    public void onStop() {
        logger.info("Exiting");
        if (pending.isEmpty()) {
            return;
        }
        CompletableFuture<?> chain = completedFuture(true);
        for (final var it : pending.values()) {
            chain = chain.thenCompose(ignored -> it.exceptionally(e -> null));
        }
        try {
            chain.toCompletableFuture().get(1, MINUTES);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (final TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onModify(final GatlingBenchmark resource) {
        try {
            logger.finest("Modification of a benchmark is not supported since it is assumed already scheduled");
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, () -> "Can't handle resource mutation: " + resource);
        }
    }

    @Override
    public void onAdd(final GatlingBenchmark resource) {
        try {
            pending.compute(resource.metadata().name(), (k, previous) -> {
                try {
                    final var trigger = ofNullable(previous)
                            .orElseGet(() -> completedFuture(null))
                            .exceptionally(e -> null)
                            .thenCompose(ignored -> trigger(resource));
                    // do not remove there if it is a deletion
                    trigger.whenCompleteAsync(
                            (ok, ko) -> {
                                if (ko != null) {
                                    logger.log(
                                            SEVERE,
                                            ko,
                                            () -> "An error occurred triggering '"
                                                    + resource.metadata().name() + "': " + ko.getMessage());
                                    // will be removed from pending by setStatus()
                                    setStatus(
                                            resource.metadata().name(),
                                            trigger,
                                            new GatlingBenchmarkStatus(
                                                    FAILED,
                                                    new GatlingBenchmarkStatus.Message(
                                                            "Failed when deploying orchestrator", ko.getMessage()),
                                                    Integer.MIN_VALUE));
                                } else {
                                    pending.remove(resource.metadata().name(), trigger);
                                }
                            },
                            scheduledExecutorService);
                    return trigger;
                } catch (final RuntimeException re) {
                    pending.remove(resource.metadata().name());
                    throw re;
                }
            });
            super.onAdd(resource);
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, () -> "Can't handle resource addition: " + resource);
            pending.put(
                    resource.metadata().name(),
                    setStatus(
                            resource.metadata().name(),
                            null,
                            new GatlingBenchmarkStatus(
                                    FAILED,
                                    new GatlingBenchmarkStatus.Message(
                                            "Failed when creating orchestrator", re.getMessage()),
                                    Integer.MIN_VALUE)));
        }
    }

    @Override
    public void onDelete(final GatlingBenchmark resource) {
        try {
            pending.compute(resource.metadata().name(), (k, creating) -> {
                final var action =
                        creating == null ? doDelete(resource) : creating.thenCompose(created -> doDelete(resource));
                action.whenCompleteAsync(
                        (ok, ko) -> pending.remove(resource.metadata().name(), action), scheduledExecutorService);
                return action;
            });
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, () -> "Can't handle resource deletion: " + resource);
        }
    }

    // todo: add retries?
    private CompletionStage<?> setStatus(
            final String name, final CompletionStage<?> enclosingPromiseToRemove, final GatlingBenchmarkStatus status) {
        final var uri = URI.create(baseUri + '/' + name + "/status");
        try {
            final var payload = "{\"status\":" + json.toString(status) + "}";
            final var self = kubernetes.sendAsync(
                    HttpRequest.newBuilder()
                            .method("PATCH", HttpRequest.BodyPublishers.ofString(payload, UTF_8))
                            .uri(uri)
                            .header("content-type", "application/merge-patch+json")
                            .build(),
                    ofString());
            return self.whenCompleteAsync(
                    (ok, ko) -> {
                        if (ok == null || ok.statusCode() > 299) {
                            logger.warning(() -> "Can't update status of benchmark '" + name + "': "
                                    + (ok == null ? "?" : ok.body()));
                        }
                        pending.remove(name, enclosingPromiseToRemove == null ? self : enclosingPromiseToRemove);
                    },
                    scheduledExecutorService);
        } catch (final RuntimeException re) {
            logger.log(WARNING, re, () -> "Can't update status of '" + name + "' to " + status);
            throw new IllegalStateException(re);
        }
    }

    // trigger is as "simple" as launching an orchestrator
    private CompletionStage<?> trigger(final GatlingBenchmark benchmark) {
        return deployer.deploy(
                "gatling-operator#generic-job#fire-and-forget",
                benchmark.spec().timeout() == null
                        ? 14_400_000L
                        : benchmark.spec().timeout(),
                toPlaceholders(benchmark),
                null);
    }

    // note: this can be enhanced enabling to override and merge some placeholders like env, labels ones
    private Map<String, String> toPlaceholders(final GatlingBenchmark benchmark) {
        final var placeholders =
                new HashMap<>(configuration.orchestrator() == null ? Map.of() : configuration.orchestrator());
        // set global placeholders since they will be interpreted there - easier cause it doesn't need escaping
        placeholders.putAll(Map.of(
                "gatling-operator.implicit.version",
                VersionHolder.VERSION,
                "gatling-operator.implicit.parent-name",
                benchmark.metadata().name(),
                // encourage cleanup - otherwise to setup manually
                "generic-job.labels",
                "{\"gatling.yupiik.io/parent-name\":\"" + benchmark.metadata().name() + "\"}"));
        placeholders.put(
                "generic-job.name", computeOrchestratorName(benchmark.metadata().name()));
        placeholders.putIfAbsent("generic-job.image", "yupiik/gatling-cli:" + VersionHolder.VERSION);
        placeholders.putIfAbsent(
                "generic-job.imagePullPolicy", VersionHolder.VERSION.endsWith("-SNAPSHOT") ? "Always" : "IfNotPresent");
        placeholders.putIfAbsent( // todo merge if existing
                "generic-job.env",
                json.toString(
                        List.of(
                                Map.of(
                                        "name",
                                        "K8S_POD_IP",
                                        "valueFrom",
                                        Map.of("fieldRef", Map.of("fieldPath", "status.podIP"))),
                                Map.of(
                                        "name",
                                        "_JAVA_OPTIONS",
                                        "value",
                                        "-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75 -Djdk.httpclient.keepalive.timeout=30 -Dsun.net.inetaddr.ttl=60 -Dio.yupiik.logging.jul.handler.AsyncHandler.formatter=json"))));
        placeholders.put( // todo: merge with config?
                "generic-job.labels",
                json.toString(Map.of(
                        CRD_LABEL, benchmark.metadata().name(),
                        START_LABEL, Long.toString(clock.instant().toEpochMilli()))));
        placeholders.put( // else use overriden one
                "generic-job.command",
                json.toString(List.of( // assume jib
                        "java",
                        "-XX:+ExitOnOutOfMemoryError",
                        "-XX:MaxRAMPercentage=75",
                        "-Djdk.httpclient.keepalive.timeout=30",
                        "-Dsun.net.inetaddr.ttl=60",
                        "-Djava.util.logging.manager=io.yupiik.logging.jul.YupiikLogManager",
                        "-Dio.yupiik.logging.jul.handler.AsyncHandler.formatter=json",
                        "-cp",
                        "@/opt/yupiik/gatling-operator/gatling-cli/jib-classpath-file",
                        Launcher.class.getName())));
        placeholders.put(
                "generic-job.args",
                json.toString(Stream.concat(
                                Stream.of(
                                        "bench",
                                        "--benchmark-base-uri",
                                        baseUri + '/' + benchmark.metadata().name()),
                                toCli(benchmark.spec()))
                        .toList()));
        return placeholders;
    }

    private Stream<String> toCli(final GatlingBenchmarkSpec spec) {
        final var index = new AtomicInteger();
        return Stream.<Stream<String>>of(
                        spec.autoClean() == null
                                ? Stream.empty()
                                : Stream.of("--spec-auto-clean", Boolean.toString(spec.autoClean())),
                        spec.timeout() == null
                                ? Stream.empty()
                                : Stream.of("--spec-auto-timeout", Long.toString(spec.timeout())),
                        Stream.of(
                                "--spec-pipeline-length",
                                spec.pipeline() == null
                                        ? "0"
                                        : Integer.toString(spec.pipeline().size())),
                        spec.pipeline() == null
                                ? Stream.empty()
                                : spec.pipeline().stream().flatMap(it -> {
                                    final var idx = index.getAndIncrement();
                                    final var prefix = "--spec-pipeline-" + idx + "-";
                                    return Stream.of(
                                                    Stream.of(
                                                            prefix + "name",
                                                            it.name(),
                                                            prefix + "range",
                                                            Integer.toString(it.range()),
                                                            prefix + "placeholders",
                                                            it.placeholders() == null
                                                                    ? ""
                                                                    : toPlaceholdersCliValue(it.placeholders())),
                                                    it.deleteRange() == null
                                                            ? Stream.<String>empty()
                                                            : Stream.of(
                                                                    prefix + "deleteRange",
                                                                    Integer.toString(it.deleteRange())),
                                                    it.deleteRange() == null
                                                            ? Stream.<String>empty()
                                                            : Stream.of(
                                                                    prefix + "executeCondition",
                                                                    it.executeCondition()
                                                                            .name()))
                                            .flatMap(identity());
                                }))
                .flatMap(it -> it);
    }

    private String toPlaceholdersCliValue(final Map<String, String> placeholders) {
        final var props = new Properties();
        props.putAll(placeholders);
        final var writer = new StringWriter();
        try (writer) {
            props.store(writer, "");
        } catch (final IOException e) { // more than unlikely
            logger.severe("Can't write properties");
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    private Map<String, String> merge(final Map<String, String> a, final Map<String, String> b) {
        return Stream.of(a, b)
                .filter(Objects::nonNull)
                .flatMap(it -> it.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (p, s) -> p));
    }

    private String computeOrchestratorName(final String name) {
        final var date = "-" + clock.instant().atOffset(ZoneOffset.UTC).format(dateTimeFormatter);
        var orchestratorName = name;
        if (orchestratorName.length() + date.length() > 63) {
            var toRemove = 63 /* to comply to dns names */ - orchestratorName.length() - date.length() - 1;
            orchestratorName = orchestratorName.substring(0, orchestratorName.length() - toRemove);
        }

        return orchestratorName + date;
    }

    // 1. delete orchestrator
    // 2. delete gatling workers
    // 3. delete post pods
    // -> they are all jobs sharing the labels xxxxx
    // note that we do not use delete collection to not require this permission but just a plain delete for now
    //
    // we do it by concurrently deleting all related jobs but we also handle services for reporter command
    private CompletableFuture<Void> doDelete(final GatlingBenchmark resource) {
        return allOf(doDelete(baseJobsUri, resource), doDelete(baseServicesUri, resource));
    }

    private CompletableFuture<Void> doDelete(final String base, final GatlingBenchmark resource) {
        return kubernetes
                .sendAsync(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(URI.create(base + "?limit=500&"
                                        + // unlikely we get > 500 jobs, ie ~500 gatling instances + orchestrator + post
                                        // jobs if any
                                        "labelSelector="
                                        + URLEncoder.encode(
                                                CRD_LABEL + "="
                                                        + resource.metadata().name(),
                                                UTF_8)))
                                .build(),
                        ofString())
                .thenCompose(res -> {
                    if (res.statusCode() != 200) {
                        logger.severe(
                                () -> "Can't cleanup '" + resource.metadata().name() + "' CRD, check all '" + base
                                        + "' with the label " + CRD_LABEL + " matching the CRD name");
                        return completedFuture(null);
                    }

                    final var wrapper = json.fromString(GenericItems.class, res.body());
                    if (wrapper.items().isEmpty()) {
                        logger.info(() -> "No '" + base + "' to clean up for '"
                                + resource.metadata().name() + "'");
                        return completedFuture(null);
                    }

                    logger.info(() -> "Deleting '" + base + "' related to '"
                            + resource.metadata().name() + "': #"
                            + wrapper.items().size());

                    final var all = wrapper.items().stream()
                            .map(it -> kubernetes
                                    .sendAsync(
                                            HttpRequest.newBuilder()
                                                    .method(
                                                            "DELETE",
                                                            HttpRequest.BodyPublishers.ofString(
                                                                    "{\"kind\":\"DeleteOptions\",\"apiVersion\":\"v1\",\"propagationPolicy\":\"Background\",\"gracePeriodSeconds\":60}"))
                                                    .uri(URI.create(base + "/"
                                                            + it.metadata().name()))
                                                    .build(),
                                            ofString())
                                    .thenAccept(deleted -> {
                                        if (deleted.statusCode() > 299) {
                                            logger.severe(() -> "An error occurred deleting '"
                                                    + it.metadata().name() + "':\n" + deleted.body());
                                        }
                                    }))
                            .toArray(CompletableFuture<?>[]::new);

                    return CompletableFuture.allOf(all);
                });
    }
}
