package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.gatling.kubernetes.model.GatlingBenchmarkSpec;
import io.yupiik.gatling.kubernetes.model.GatlingBenchmarkStatus;
import io.yupiik.kubernetes.operator.base.impl.ObjectLike;

@JsonModel
public record GatlingBenchmark(Metadata metadata, GatlingBenchmarkSpec spec, GatlingBenchmarkStatus status)
        implements ObjectLike {}
