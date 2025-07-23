package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record EnvFrom(EnvSource configMapRef, String prefix, EnvSource secretRef) {
    @JsonModel
    public record EnvSource(String name, Boolean optional) {}
}
