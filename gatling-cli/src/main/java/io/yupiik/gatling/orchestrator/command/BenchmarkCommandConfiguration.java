package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.gatling.kubernetes.model.GatlingBenchmarkSpec;

@RootConfiguration("bench")
public record BenchmarkCommandConfiguration(
        @Property(
                        documentation = "Spec of the benchmark.",
                        defaultValue =
                                "new io.yupiik.gatling.kubernetes.model.GatlingBenchmarkSpec(java.util.List.of(), false)")
                GatlingBenchmarkSpec spec,
        @Property(
                        value = "benchmark-name",
                        documentation = "Name of the `GatlingBenchmark` CRD to update the status.",
                        defaultValue = "\"\"")
                String benchmarkName) {}
