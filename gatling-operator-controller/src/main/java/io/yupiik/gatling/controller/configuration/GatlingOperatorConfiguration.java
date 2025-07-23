package io.yupiik.gatling.controller.configuration;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.gatling.controller.model.PodConfiguration;

@ApplicationScoped
@RootConfiguration("gatling-operator")
public record GatlingOperatorConfiguration(
        @Property(
                        documentation = "The namespace the operator will watch `GatlingBenchmark` in.",
                        defaultValue = "\"default\"")
                String namespace,
        @Property(
                        documentation =
                                "Customizations of the deployment/runtime. It can be used to force some `Pod` on some `Node` for example.")
                RuntimeConfiguration runtime) {

    public record RuntimeConfiguration(
            @Property(
                            documentation =
                                    "Orchestrator `Pod` customizations. Note that it can be overridden by the CRD itself.")
                    PodConfiguration orchestrator,
            @Property(
                            documentation =
                                    "Injector (gatling) `Pod` customizations. Note that it can be overridden by the CRD itself.")
                    PodConfiguration injectors,
            @Property(
                            documentation =
                                    "Reporter `Pod` customizations. Note that it can be overridden by the CRD itself.")
                    PodConfiguration reporters) {}
}
