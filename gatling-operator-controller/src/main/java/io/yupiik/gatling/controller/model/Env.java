package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Env(String name, String value, EnvVarSource valueFrom) {
    @JsonModel
    public record EnvVarSource(
            ConfigMapKeySelector configMapKeyRef,
            ObjectFieldSelector fieldRef,
            ResourceFieldSelector resourceFieldRef,
            SecretKeySelector secretKeyRef) {}

    @JsonModel
    public record ConfigMapKeySelector(String key, String name, Boolean optional) {}

    @JsonModel
    public record ObjectFieldSelector(String apiVersion, String fieldPath) {}

    @JsonModel
    public record ResourceFieldSelector(String containerName, String divisor, String resource) {}

    @JsonModel
    public record SecretKeySelector(String key, String name, Boolean optional) {}
}
