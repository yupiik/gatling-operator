package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.gatling.kubernetes.model.GatlingBenchmarkSpec;

@RootConfiguration("bench")
public record BenchmarkCommandConfiguration(
        @Property(documentation = "Spec of the benchmark.") GatlingBenchmarkSpec spec,
        @Property(
                        value = "benchmark-base-uri",
                        documentation = "Base URI of the benchmark endpoint. "
                                + "It goes from to the benchmark name (end of the path). "
                                + "It is used appending `/status` and directly for auto clean if enabled.",
                        required = true)
                String baseUri) {}
