package io.yupiik.gatling.controller.configuration;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import java.util.Map;

@ApplicationScoped
@RootConfiguration("gatling-operator")
public record GatlingOperatorConfiguration(
        @Property(
                        documentation =
                                "Orchestrator global placeholders for the `gatling-operator#generic-job` alveolus used to deploy the orchestrator. "
                                        + "It can enable to force some affinity for example.")
                Map<String, String> orchestrator) {}
