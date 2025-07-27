package io.yupiik.gatling.kubernetes.model.operator;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record GatlingBenchmarkStatus(BenchmarkStatus status, Message message, int range) {
    @JsonModel
    public record Message(String operator, String detail) {}

    @JsonModel
    public enum BenchmarkStatus {
        RUNNING,
        FINISHED,
        FAILED
    }
}
