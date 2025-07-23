package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record ContainerSecurityContext(
        Boolean allowPrivilegeEscalation,
        AppArmorProfile appArmorProfile,
        Capabilities capabilities,
        Boolean privileged,
        ProcMountType procMount,
        Boolean readOnlyRootFilesystem,
        Long runAsGroup,
        Boolean runAsNonRoot,
        Long runAsUser,
        SeLinuxOptions seLinuxOptions,
        SeccompProfile seccompProfile) {
    public static boolean isEmpty(final ContainerSecurityContext ctx) {
        return ctx == null
                || (ctx.allowPrivilegeEscalation() == null
                        && AppArmorProfile.isEmpty(ctx.appArmorProfile())
                        && Capabilities.isEmpty(ctx.capabilities())
                        && ctx.privileged() == null
                        && ctx.procMount() == null
                        && ctx.readOnlyRootFilesystem() == null
                        && ctx.runAsGroup() == null
                        && ctx.runAsNonRoot() == null
                        && ctx.runAsUser() == null
                        && SeLinuxOptions.isEmpty(ctx.seLinuxOptions())
                        && SeccompProfile.isEmpty(ctx.seccompProfile()));
    }
}
