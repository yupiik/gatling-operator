package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkSpec;
import io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkStatus;
import io.yupiik.kubernetes.operator.base.impl.ObjectLike;

@JsonModel
public record GatlingBenchmark(Metadata metadata, GatlingBenchmarkSpec spec, GatlingBenchmarkStatus status)
        implements ObjectLike {}
