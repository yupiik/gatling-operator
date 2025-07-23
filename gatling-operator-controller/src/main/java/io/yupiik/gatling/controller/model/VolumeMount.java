package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

@JsonModel
public record VolumeMount(
        String name,
        String mountPath,
        MountPropagation mountPropagation,
        Boolean readOnly,
        RecursiveReadOnly recursiveReadOnly,
        String subPath,
        String subPathExpr) {
    @JsonModel
    public enum MountPropagation {
        None,
        HostToContainer,
        Bidirectional
    }

    @JsonModel
    public enum RecursiveReadOnly {
        Disabled,
        IfPossible,
        Enabled
    }
}
