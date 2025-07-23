package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;

@JsonModel
public record PodSecurityContext(
        AppArmorProfile appArmorProfile,
        Long fsGroup,
        String fsGroupChangePolicy,
        Long runAsGroup,
        Boolean runAsNonRoot,
        Long runAsUser,
        String seLinuxChangePolicy,
        SeLinuxOptions seLinuxOptions,
        SeccompProfile seccompProfile,
        List<Long> supplementalGroups,
        String supplementalGroupsPolicy,
        List<Sysctl> sysctls) {
    public static boolean isEmpty(final PodSecurityContext ctx) {
        return ctx == null
                || (AppArmorProfile.isEmpty(ctx.appArmorProfile())
                        && ctx.fsGroup() == null
                        && ctx.fsGroupChangePolicy() == null
                        && ctx.runAsGroup() == null
                        && ctx.runAsNonRoot() == null
                        && ctx.runAsUser() == null
                        && ctx.seLinuxChangePolicy() == null
                        && SeLinuxOptions.isEmpty(ctx.seLinuxOptions())
                        && SeccompProfile.isEmpty(ctx.seccompProfile())
                        && (ctx.supplementalGroups() == null
                                || ctx.supplementalGroups().isEmpty())
                        && ctx.supplementalGroupsPolicy() == null
                        && (ctx.sysctls() == null || ctx.sysctls().isEmpty()));
    }

    @JsonModel
    public record Sysctl(String name, String value) {}
}
