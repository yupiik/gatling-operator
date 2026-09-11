package io.yupiik.gatling.controller.bundlebee;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yupiik.bundlebee.core.kube.HttpKubeClient;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import io.yupiik.bundlebee.core.service.ArchiveReader;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.enterprise.inject.se.SeContainer;
import org.junit.jupiter.api.Test;

class BundleBeeBeansTest {
    // simple smoke test to ensure we can deploy an alveolus
    @Test
    void apply() throws ExecutionException, InterruptedException {
        try (final var container = ConfiguringContainer.of().start();
                final var handler = container.lookup(AlveolusHandler.class);
                final var archiveReader = container.lookup(ArchiveReader.class)) {
            final var descriptors = new ArrayList<AlveolusHandler.LoadedDescriptor>();
            final var result = handler.instance()
                    .findRootAlveoli("auto", "skip", "junit", "junit")
                    .thenCompose(it -> {
                        assertEquals(1, it.size());
                        final var deployable = it.getFirst();
                        return handler.instance()
                                .executeOnceOnAlveolus(
                                        "Junit",
                                        deployable.getManifest(),
                                        deployable.getAlveolus(),
                                        null,
                                        (ctx, desc) -> completedFuture(true),
                                        archiveReader.instance().newCache(),
                                        desc -> {
                                            descriptors.add(desc);
                                            return completedFuture(null);
                                        },
                                        "junit",
                                        "junit");
                    })
                    .toCompletableFuture()
                    .get();
            assertEquals(1L, result);
            assertEquals(
                    List.of("bundlebee/kubernetes/configmap.json"),
                    descriptors.stream()
                            .map(AlveolusHandler.LoadedDescriptor::getResource)
                            .toList());
        }
    }

    // ensure we switched the client used by bundlebee
    @Test
    void checkK8sClient() {
        try (final var container = ConfiguringContainer.of().start();
                final var cdi = container.lookup(SeContainer.class)) {
            assertEquals(
                    FusionBundleBeeHttpClient.class,
                    // application scoped so it is a proxy subclass otherwise
                    cdi.instance().select(HttpKubeClient.class).get().getClass().getSuperclass());
        }
    }
}
