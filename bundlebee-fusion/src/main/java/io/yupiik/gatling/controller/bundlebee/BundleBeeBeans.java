package io.yupiik.gatling.controller.bundlebee;

import io.yupiik.bundlebee.core.kube.DefaultHttpKubeClient;
import io.yupiik.bundlebee.core.kube.HttpKubeClient;
import io.yupiik.bundlebee.core.kube.KubeClient;
import io.yupiik.bundlebee.core.lang.SubstitutorProducer;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import io.yupiik.bundlebee.core.service.ArchiveReader;
import io.yupiik.bundlebee.core.service.ConditionAwaiter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBeanAttributes;

// until bundlebee is fusion native we do this bridge
@ApplicationScoped
public class BundleBeeBeans {
    @Bean
    @ApplicationScoped
    public SeContainer cdi(final FusionBundleBeeHttpClient httpClient) {
        return SeContainerInitializer.newInstance()
                // replace default client by fusion one for consistency in the operator
                .addExtensions(new Extension() {
                    void onDefaultClient(@Observes final ProcessBeanAttributes<DefaultHttpKubeClient> defaultClient) {
                        defaultClient.veto();
                    }

                    void addClient(@Observes final AfterBeanDiscovery afterBeanDiscovery) {
                        afterBeanDiscovery
                                .addBean()
                                // we use a fusion application scoped one so this avoids yet another proxy
                                .scope(Dependent.class)
                                .id(FusionBundleBeeHttpClient.class.getName())
                                .beanClass(FusionBundleBeeHttpClient.class)
                                .types(HttpKubeClient.class, Object.class)
                                .createWith(c -> httpClient);
                    }
                })
                .initialize();
    }

    @Bean
    @ApplicationScoped
    public AlveolusHandler alveolusHandler(final SeContainer container) {
        return container.select(AlveolusHandler.class).get();
    }

    @Bean
    @ApplicationScoped
    public ConditionAwaiter conditionAwaiter(final SeContainer container) {
        return container.select(ConditionAwaiter.class).get();
    }

    @Bean
    @ApplicationScoped
    public ArchiveReader archiveReader(final SeContainer container) {
        return container.select(ArchiveReader.class).get();
    }

    @Bean
    @ApplicationScoped
    public SubstitutorProducer substitutorProducer(final SeContainer container) {
        return container.select(SubstitutorProducer.class).get();
    }

    @Bean
    @ApplicationScoped
    public KubeClient kubeClient(final SeContainer container) {
        return container.select(KubeClient.class).get();
    }
}
