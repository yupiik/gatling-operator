package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record SeLinuxOptions(String level, String role, String type, String user) {
    public static boolean isEmpty(final SeLinuxOptions o) {
        return o == null || (o.level() == null && o.role() == null && o.type() == null && o.user() == null);
    }
}
