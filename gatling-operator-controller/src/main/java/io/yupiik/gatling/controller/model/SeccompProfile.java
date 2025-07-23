package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record SeccompProfile(SecurityContextProfileType type, String localhostProfile) {
    public static boolean isEmpty(final SeccompProfile profile) {
        return profile == null || (profile.type() == null && profile.localhostProfile() == null);
    }
}
