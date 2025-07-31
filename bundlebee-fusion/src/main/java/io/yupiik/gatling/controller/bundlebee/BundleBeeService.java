package io.yupiik.gatling.controller.bundlebee;

import io.yupiik.bundlebee.core.kube.KubeClient;
import io.yupiik.bundlebee.core.lang.SubstitutorProducer;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import io.yupiik.bundlebee.core.service.ArchiveReader;
import io.yupiik.bundlebee.core.service.ConditionAwaiter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class BundleBeeService {
    private final AlveolusHandler handler;
    private final SubstitutorProducer substitutorProducer;
    private final ArchiveReader.Cache cache;
    private final KubeClient kubeClient; // actually a preprocessor/postprocessor not the actual client in bundlebee
    private final ConditionAwaiter conditionAwaiter;
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicLong id = new AtomicLong();
    private final ConcurrentMap<String, CompletionStage<AlveolusHandler.ManifestAndAlveolus>> alveolusCache =
            new ConcurrentHashMap<>();

    public BundleBeeService(
            final AlveolusHandler handler,
            final SubstitutorProducer substitutorProducer,
            final ArchiveReader archiveReader,
            final ConditionAwaiter conditionAwaiter,
            final ScheduledExecutorService scheduledExecutorService,
            final KubeClient kubeClient) {
        this.handler = handler;
        this.substitutorProducer = substitutorProducer;
        this.kubeClient = kubeClient;
        this.conditionAwaiter = conditionAwaiter;
        this.scheduledExecutorService = scheduledExecutorService;
        this.cache = archiveReader == null ? null : archiveReader.newCache();
    }

    public CompletionStage<AlveolusHandler.ManifestAndAlveolus> resolve(final String alveolus) {
        return alveolusCache
                // todo: enable to configure the sources
                .computeIfAbsent(
                alveolus, k -> handler.findRootAlveoli("auto", "skip", k, k).thenApply(List::getFirst));
    }

    // simplified version of bundlebee.apply command
    public CompletionStage<?> deploy(
            final String alveolus, final long awaitTimeout, final Map<String, String> placeholders) {
        final var id = Long.toString(this.id.incrementAndGet());
        substitutorProducer.getByIdContextualPlaceholders().put(id, placeholders);
        try {
            return resolve(alveolus)
                    .thenCompose(it -> handler.executeOnceOnAlveolus(
                            "Deploying",
                            it.getManifest(),
                            it.getAlveolus(),
                            null,
                            (ctx, desc) -> kubeClient.apply(desc.getContent(), desc.getExtension(), Map.of(), true),
                            cache,
                            desc -> conditionAwaiter.await("apply", desc, scheduledExecutorService, awaitTimeout),
                            "deployed",
                            id))
                    .whenComplete((ok, ko) ->
                            substitutorProducer.getByIdContextualPlaceholders().remove(id));
        } catch (final RuntimeException re) {
            substitutorProducer.getByIdContextualPlaceholders().remove(id);
            throw re;
        }
    }
}
