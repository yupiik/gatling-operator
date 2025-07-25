package io.yupiik.gatling.controller.bundlebee;

import static java.util.concurrent.CompletableFuture.completedFuture;

import io.yupiik.bundlebee.core.kube.KubeClient;
import io.yupiik.bundlebee.core.lang.SubstitutorProducer;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import io.yupiik.bundlebee.core.service.ArchiveReader;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class BundleBeeService {
    private final AlveolusHandler handler;
    private final SubstitutorProducer substitutorProducer;
    private final ArchiveReader.Cache cache;
    private final KubeClient kubeClient; // actually a preprocessor/postprocessor not the actual client in bundlebee
    private final AtomicLong id = new AtomicLong();
    private final ConcurrentMap<String, CompletionStage<AlveolusHandler.ManifestAndAlveolus>> alveolusCache =
            new ConcurrentHashMap<>();

    public BundleBeeService(
            final AlveolusHandler handler,
            final SubstitutorProducer substitutorProducer,
            final ArchiveReader archiveReader,
            final KubeClient kubeClient) {
        this.handler = handler;
        this.substitutorProducer = substitutorProducer;
        this.kubeClient = kubeClient;
        this.cache = archiveReader == null ? null : archiveReader.newCache();
    }

    // simplified version of bundlebee.apply command
    public CompletionStage<?> deploy(final String alveolus, final Map<String, String> placeholders) {
        final var id = Long.toString(this.id.incrementAndGet());
        substitutorProducer.getByIdContextualPlaceholders().put(id, placeholders);
        try {
            return alveolusCache
                    .computeIfAbsent(alveolus, k -> handler.findRootAlveoli("auto", "skip", k, k)
                            .thenApply(List::getFirst))
                    .thenCompose(it -> handler.executeOnceOnAlveolus(
                            "Deploying",
                            it.getManifest(),
                            it.getAlveolus(),
                            null,
                            (ctx, desc) -> kubeClient.apply(desc.getContent(), desc.getExtension(), Map.of(), true),
                            cache,
                            desc -> completedFuture(null),
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
