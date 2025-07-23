package io.yupiik.gatling.controller.listener;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.toMap;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.kubernetes.crd.CustomResourceDefinition;
import io.yupiik.fusion.framework.build.api.kubernetes.crd.CustomResourceDefinition.PrinterColumn;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.gatling.controller.configuration.GatlingOperatorConfiguration;
import io.yupiik.gatling.controller.model.Env;
import io.yupiik.gatling.controller.model.GatlingBenchmark;
import io.yupiik.gatling.controller.model.Jobs;
import io.yupiik.gatling.controller.model.PodConfiguration;
import io.yupiik.gatling.controller.service.BundleBeeService;
import io.yupiik.gatling.controller.version.VersionHolder;
import io.yupiik.kubernetes.operator.base.spi.Operator;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Stream;

@CustomResourceDefinition(
        /* defaults
        namespaced = true,
         */
        group = GatlingBenchmarkOperator.GROUP,
        version = GatlingBenchmarkOperator.VERSION,
        name = GatlingBenchmarkOperator.NAME,
        shortNames = {"gb"},
        spec = GatlingBenchmark.Spec.class,
        status = GatlingBenchmark.Status.class,
        additionalPrinterColumns = {
            @PrinterColumn(name = "Image", type = "string", jsonPath = ".spec.gatling.image"),
            @PrinterColumn(name = "Status", type = "string", jsonPath = ".status.status")
        },
        selectableFields = {
            ".spec.gatling.image",
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
    private final BundleBeeService deployer;
    private final Clock clock;
    private final GatlingOperatorConfiguration configuration;

    // simple and likely sufficient (even if not 100% accurate)
    // way to avoid to delete too early or to exit in the middle of a create
    private final Map<String, CompletionStage<?>> pending = new HashMap<>();

    // for subclassing/proxy
    protected GatlingBenchmarkOperator() {
        super(null, null);
        this.scheduledExecutorService = null;
        this.kubernetes = null;
        this.json = null;
        this.baseJobsUri = null;
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
                        List.of(configuration.namespace()),
                        NAME.toLowerCase(Locale.ROOT) + "s",
                        GROUP + "/" + VERSION));
        this.clock = clock;
        this.kubernetes = client;
        this.json = json;
        this.deployer = deployer;
        this.scheduledExecutorService = executorService;
        this.configuration = configuration;
        this.baseJobsUri = "https://kubernetes.api/api/v1/namespaces/" + configuration.namespace() + "/jobs";
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
            pending.computeIfAbsent(resource.metadata().name(), k -> {
                try {
                    final var trigger = trigger(resource);
                    // do not remove there if it is a deletion
                    trigger.whenComplete(
                            (ok, ko) -> pending.remove(resource.metadata().name(), trigger));
                    return trigger;
                } catch (final RuntimeException re) {
                    pending.remove(resource.metadata().name());
                    throw re;
                }
            });
            super.onAdd(resource);
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, () -> "Can't handle resource addition: " + resource);
        }
    }

    @Override
    public void onDelete(final GatlingBenchmark resource) {
        try {
            pending.compute(resource.metadata().name(), (k, creating) -> {
                final var action =
                        creating == null ? doDelete(resource) : creating.thenCompose(created -> doDelete(resource));
                action.whenComplete(
                        (ok, ko) -> pending.remove(resource.metadata().name(), action));
                return action;
            });
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, () -> "Can't handle resource deletion: " + resource);
        }
    }

    // trigger is as "simple" as launching an orchestrator
    private CompletionStage<?> trigger(final GatlingBenchmark benchmark) {
        final var placeholders = new HashMap<String, String>();

        // defaults
        placeholders.put(
                "orchestrator.name",
                computeOrchestratorName(benchmark.metadata().name()));
        placeholders.put("orchestrator.image", "yupiik/gatling-cli:" + VersionHolder.VERSION.toLowerCase(Locale.ROOT));
        placeholders.put(
                "orchestrator.imagePullPolicy",
                VersionHolder.VERSION.endsWith("-SNAPSHOT") ? "Always" : "IfNotPresent");

        final var env = new ArrayList<>(List.of(new Env(
                "K8S_POD_IP",
                null,
                new Env.EnvVarSource(null, new Env.ObjectFieldSelector(null, "status.podIP"), null, null))));
        final var labels = new HashMap<>(Map.of(CRD_LABEL, benchmark.metadata().name()));

        // overrides
        final var pod =
                merge(benchmark.spec().orchestrator(), configuration.runtime().orchestrator());
        if (pod != null) {
            if (pod.affinity() != null) {
                placeholders.put("orchestrator.affinity", json.toString(pod.affinity()));
            }
            if (pod.nodeSelector() != null) {
                placeholders.put("orchestrator.nodeSelector", json.toString(pod.nodeSelector()));
            }
            if (pod.tolerations() != null) {
                placeholders.put("orchestrator.tolerations", json.toString(pod.tolerations()));
            }
            if (pod.dnsConfig() != null) {
                placeholders.put("orchestrator.dnsConfig", json.toString(pod.dnsConfig()));
            }
            if (pod.activeDeadlineSeconds() != null) {
                placeholders.put("orchestrator.activeDeadlineSeconds", Long.toString(pod.activeDeadlineSeconds()));
            }
            if (pod.ttlSecondsAfterFinished() != null) {
                placeholders.put("orchestrator.ttlSecondsAfterFinished", Long.toString(pod.ttlSecondsAfterFinished()));
            }
            if (pod.podSecurityContext() != null) {
                placeholders.put("orchestrator.podSecurityContext", json.toString(pod.podSecurityContext()));
            }
            if (pod.containerSecurityContext() != null) {
                placeholders.put(
                        "orchestrator.containerSecurityContext", json.toString(pod.containerSecurityContext()));
            }
            if (pod.resources() != null) {
                placeholders.put("orchestrator.resources", json.toString(pod.resources()));
            }
            if (pod.image() != null) {
                placeholders.put("orchestrator.image", pod.image());
            }
            if (pod.imagePullPolicy() != null) {
                placeholders.put("orchestrator.imagePullPolicy", pod.imagePullPolicy());
            }
            if (pod.imagePullSecrets() != null) {
                placeholders.put("orchestrator.imagePullSecrets", json.toString(pod.imagePullSecrets()));
            }
            if (pod.initContainers() != null) {
                placeholders.put("orchestrator.initContainers", json.toString(pod.initContainers()));
            }
            if (pod.labels() != null) {
                labels.putAll(pod.labels());
            }
            if (pod.annotations() != null) {
                placeholders.put("orchestrator.annotations", json.toString(pod.annotations()));
            }
            if (pod.podLabels() != null) {
                placeholders.put("orchestrator.podLabels", json.toString(pod.podLabels()));
            }
            if (pod.podAnnotations() != null) {
                placeholders.put("orchestrator.podAnnotations", json.toString(pod.podAnnotations()));
            }
            if (pod.env() != null) {
                env.addAll(pod.env());
            }
        }
        if (!env.isEmpty()) {
            placeholders.put("orchestrator.env", json.toString(env));
        }

        // specific
        placeholders.put("orchestrator.labels", json.toString(labels));

        // todo once orchestrator is dev
        final var injector =
                merge(benchmark.spec().injector(), configuration.runtime().injectors());
        final var reporters = benchmark.spec().reporters() == null
                        || benchmark.spec().reporters().isEmpty()
                ? List.of()
                : benchmark.spec().reporters().stream()
                        .map(it -> merge(it, configuration.runtime().reporters()))
                        .toList();
        placeholders.put(
                "orchestrator.args",
                json.toString(List.of(
                        // todo
                        )));
        // todo: +pass other pod configurations as string as well directly?

        return deployer.deployOrchestrator(placeholders);
    }

    private Map<String, String> merge(final Map<String, String> a, final Map<String, String> b) {
        return Stream.of(a, b)
                .filter(Objects::nonNull)
                .flatMap(it -> it.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (p, s) -> p));
    }

    private PodConfiguration merge(final PodConfiguration specific, final PodConfiguration globals) {
        if (specific == null) {
            return globals;
        }
        if (globals == null) {
            return specific;
        }
        return new PodConfiguration( // for now it is a one level merge
                specific.affinity() == null ? globals.affinity() : specific.affinity(),
                specific.nodeSelector() == null ? globals.nodeSelector() : specific.nodeSelector(),
                specific.tolerations() == null ? globals.tolerations() : specific.tolerations(),
                specific.env() == null
                        ? globals.env()
                        : (globals.env() == null
                                ? specific.env()
                                : Stream.concat(
                                                specific.env().stream(),
                                                globals.env().stream().filter(it -> specific.env().stream()
                                                        .noneMatch(e -> Objects.equals(e.name(), it.name()))))
                                        .toList()),
                specific.dnsConfig() == null ? globals.dnsConfig() : specific.dnsConfig(),
                specific.podSecurityContext() == null ? globals.podSecurityContext() : specific.podSecurityContext(),
                specific.containerSecurityContext() == null
                        ? globals.containerSecurityContext()
                        : specific.containerSecurityContext(),
                specific.ttlSecondsAfterFinished() == null
                        ? globals.ttlSecondsAfterFinished()
                        : specific.ttlSecondsAfterFinished(),
                specific.activeDeadlineSeconds() == null
                        ? globals.activeDeadlineSeconds()
                        : specific.activeDeadlineSeconds(),
                specific.resources() == null ? globals.resources() : specific.resources(),
                specific.image() == null ? globals.image() : specific.image(),
                specific.imagePullPolicy() == null ? globals.imagePullPolicy() : specific.imagePullPolicy(),
                specific.imagePullSecrets() == null ? globals.imagePullSecrets() : specific.imagePullSecrets(),
                specific.initContainers() == null
                        ? globals.initContainers()
                        : (globals.initContainers() == null
                                ? specific.initContainers()
                                : Stream.concat(
                                                specific.initContainers().stream(),
                                                globals.initContainers().stream()
                                                        .filter(it -> specific.initContainers().stream()
                                                                .noneMatch(e -> Objects.equals(e.name(), it.name()))))
                                        .toList()),
                merge(specific.labels(), globals.labels()),
                merge(specific.annotations(), globals.annotations()),
                merge(specific.podLabels(), globals.podLabels()),
                merge(specific.podAnnotations(), globals.podAnnotations()));
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
    private CompletableFuture<Void> doDelete(final GatlingBenchmark resource) {
        return kubernetes
                .sendAsync(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(URI.create(baseJobsUri + "?" + "limit=500&"
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
                        logger.severe(() ->
                                "Can't cleanup '" + resource.metadata().name() + "' CRD, check all jobs with the label "
                                        + CRD_LABEL + " matching the CRD name");
                        return completedFuture(null);
                    }

                    final var jobs = json.fromString(Jobs.class, res.body());
                    if (jobs.items().isEmpty()) {
                        logger.info(() ->
                                "No job to clean up for '" + resource.metadata().name() + "'");
                        return completedFuture(null);
                    }

                    logger.info(() -> "Deleting jobs related to '"
                            + resource.metadata().name() + "': #" + jobs.items().size());

                    final var all = jobs.items().stream()
                            .map(it -> kubernetes
                                    .sendAsync(
                                            HttpRequest.newBuilder()
                                                    .method(
                                                            "DELETE",
                                                            HttpRequest.BodyPublishers.ofString(
                                                                    "{\"kind\":\"DeleteOptions\",\"apiVersion\":\"v1\",\"propagationPolicy\":\"Background\",\"gracePeriodSeconds\":60}"))
                                                    .uri(URI.create(baseJobsUri + "/"
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
