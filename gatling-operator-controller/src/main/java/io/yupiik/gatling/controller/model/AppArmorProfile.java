package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record AppArmorProfile(SecurityContextProfileType type, String localhostProfile) {
    public static boolean isEmpty(final AppArmorProfile appArmorProfile) {
        return appArmorProfile == null
                || (appArmorProfile.type() == null && appArmorProfile.localhostProfile() == null);
    }
}
