package io.yupiik.gatling.controller.infra;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;

public class JUnitModule implements FusionModule {
    @Override
    public BiPredicate<RuntimeContainer, FusionBean<?>> beanFilter() {
        return (c, b) -> b.type() != Clock.class || b instanceof ProvidedInstanceBean<?>;
    }

    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.of(
                new ProvidedInstanceBean<>(ApplicationScoped.class, Clock.class, ConstantClock::new),
                new BaseBean<Kubernetes>(Kubernetes.class, ApplicationScoped.class, 1_000, Map.of()) {
                    @Override
                    public Kubernetes create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return lookup(container, ExtensionContext.class, dependents)
                                .getStore(KubernetesServerExtension.KEY)
                                .get(Kubernetes.class, Kubernetes.class);
                    }
                });
    }
}
