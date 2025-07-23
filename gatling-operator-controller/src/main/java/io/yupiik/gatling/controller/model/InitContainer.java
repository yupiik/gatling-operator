package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;

// just a partial mapping for potentially useful fields
@JsonModel
public record InitContainer(
        List<String> args,
        List<String> command,
        List<Env> env,
        List<EnvFrom> envFrom,
        String image,
        String imagePullPolicy,
        String name,
        Resources resources,
        String restartPolicy,
        ContainerSecurityContext securityContext,
        List<VolumeMount> volumeMounts,
        String workingDir) {}
