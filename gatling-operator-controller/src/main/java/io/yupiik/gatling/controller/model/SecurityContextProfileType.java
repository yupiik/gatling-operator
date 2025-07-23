package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public enum SecurityContextProfileType {
    Localhost,
    RuntimeDefault,
    Unconfined
}
