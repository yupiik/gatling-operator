package io.yupiik.gatling.kubernetes.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Metadata(String name) {}
