package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.kubernetes.operator.base.impl.ObjectLike;
import java.util.List;

@JsonModel
public record GatlingBenchmark(Metadata metadata, GatlingBenchmark.Spec spec, GatlingBenchmark.Status status)
        implements ObjectLike {
    @JsonModel
    public record Spec(
            @Property(documentation = "Number of injectors.", defaultValue = "1") int parallelism,
            @Property(
                            documentation =
                                    "Orchestrator `Pod` customizations. Note that it null values are replaced by operator global ones at first level only except for `name` keyed lists.")
                    PodConfiguration orchestrator,
            @Property(
                            documentation =
                                    "Injector (gatling) `Pod` customizations. Note that it null values are replaced by operator global ones at first level only except for `name` keyed lists.")
                    PodConfiguration injector,
            @Property(
                            documentation =
                                    "Reporter `Pod` customizations. Note that it null values are replaced by operator global ones at first level only except for `name` keyed lists.")
                    List<PodConfiguration> reporters) {}

    @JsonModel
    public record Status(BenchmarkStatus status, String message) {}

    @JsonModel
    public enum BenchmarkStatus {
        RUNNING,
        FAILED
    }
}
