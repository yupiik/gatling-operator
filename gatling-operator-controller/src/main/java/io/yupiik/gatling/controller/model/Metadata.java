package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.kubernetes.operator.base.impl.MetadataLike;

@JsonModel
public record Metadata(String uid, String resourceVersion, String name) implements MetadataLike {}
