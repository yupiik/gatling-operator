package io.yupiik.gatling.controller.bundlebee;

import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

import io.yupiik.bundlebee.core.kube.ApiPreloader;
import io.yupiik.bundlebee.core.lang.SubstitutorProducer;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import io.yupiik.bundlebee.core.service.ArchiveReader;
import io.yupiik.bundlebee.core.service.ConditionAwaiter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.json.JsonObject;

@ApplicationScoped
public class BundleBeeService {
    private final AlveolusHandler handler;
    private final SubstitutorProducer substitutorProducer;
    private final ArchiveReader.Cache cache;
    private final ApiPreloader apiPreloader;
    private final ExposingKubeClient
            kubeClient; // actually a preprocessor/postprocessor not the actual client in bundlebee
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
            final ApiPreloader apiPreloader,
            final ExposingKubeClient kubeClient) {
        this.handler = handler;
        this.substitutorProducer = substitutorProducer;
        this.apiPreloader = apiPreloader;
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
            final String alveolus,
            final long awaitTimeout,
            final Map<String, String> placeholders,
            final Consumer<JsonObject> onDescriptor) {
        final var id = Long.toString(this.id.incrementAndGet());
        substitutorProducer.getByIdContextualPlaceholders().put(id, placeholders);
        try {
            final var patchedContents = new ConcurrentHashMap<String, String>();
            return resolve(alveolus)
                    .thenCompose(it -> handler.executeOnceOnAlveolus(
                            "Deploying",
                            it.getManifest(),
                            it.getAlveolus(),
                            null,
                            (ctx, desc) -> {
                                var content = desc.getContent();
                                // since we deploy with bundlebee a bundlebee deployed alveolus (this layer then bench
                                // command)
                                // escaping can be broken cause '\n' means something particular in JSON and properties
                                // formats
                                // so we catch up there when Substitutor broke it by removing only one '\' when escaping
                                // lead to 2
                                int idx = 0;
                                final var marker = "\\\\\\{{";
                                while (idx >= 0) {
                                    // 3 '\' is not possible there (in JSON) so we add one cause it means we did break
                                    // it with Substitutor
                                    final var next = content.indexOf(marker, idx);
                                    if (next < 0) {
                                        break;
                                    }
                                    if (next > 0 && content.charAt(next - 1) != '\\') {
                                        content = content.substring(0, next) + content.substring(next + 1);
                                    }
                                    idx = next
                                            + marker.length()
                                            +
                                            // at least end of the value, "}}" so doesnt hurt and enable to handle the
                                            // case we prepend a '\'
                                            2;
                                }
                                if (content != desc.getContent()) { // ref equal is faster and ok there
                                    patchedContents.put(desc.getContent(), content);
                                }
                                return kubeClient.forDescriptorWithOriginal(
                                        "Applying", content, desc.getExtension(), item -> {
                                            final var kindLowerCased = item.getPrepared()
                                                            .getString("kind")
                                                            .toLowerCase(ROOT)
                                                    + 's';
                                            if (onDescriptor != null) {
                                                onDescriptor.accept(item.getPrepared());
                                            }
                                            return apiPreloader
                                                    .ensureResourceSpec(item.getPrepared(), kindLowerCased)
                                                    .thenCompose(ignored -> kubeClient.doApply(
                                                            item.getRaw(),
                                                            item.getPrepared(),
                                                            kindLowerCased,
                                                            1,
                                                            true));
                                        });
                            },
                            cache,
                            desc -> conditionAwaiter.await(
                                    "apply",
                                    ofNullable(patchedContents.get(desc.getContent()))
                                            .map(c -> new AlveolusHandler.LoadedDescriptor(
                                                    desc.getConfiguration(),
                                                    c,
                                                    desc.getExtension(),
                                                    desc.getUri(),
                                                    desc.getResource()))
                                            .orElse(desc),
                                    scheduledExecutorService,
                                    awaitTimeout),
                            "deployed",
                            id))
                    .whenComplete((ok, ko) ->
                            substitutorProducer.getByIdContextualPlaceholders().remove(id));
        } catch (final RuntimeException re) {
            substitutorProducer.getByIdContextualPlaceholders().remove(id);
            final var promise = new CompletableFuture<Void>();
            promise.completeExceptionally(re);
            return promise;
        }
    }
}
