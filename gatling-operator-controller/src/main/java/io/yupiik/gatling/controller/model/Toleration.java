package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record Toleration(String effect, String key, String operator, Long tolerationSeconds, String value) {}
