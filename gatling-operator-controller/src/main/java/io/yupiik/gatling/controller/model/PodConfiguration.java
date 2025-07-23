package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;
import java.util.Map;

@JsonModel
public record PodConfiguration(
        @Property(documentation = "`affinity` of the `Pod`.", defaultValue = "null") Affinity affinity,
        @Property(documentation = "`nodeSelector` of the `Pod`.", defaultValue = "null")
                Map<String, String> nodeSelector,
        @Property(documentation = "`tolerations` of the `Pod`.", defaultValue = "null") List<Toleration> tolerations,
        @Property(documentation = "custom `env` of the main container.", defaultValue = "null") List<Env> env,
        @Property(documentation = "`dnsConfig` of the `Pod`.", defaultValue = "null") DnsConfig dnsConfig,
        @Property(documentation = "`security` of the `Pod`.", defaultValue = "null")
                PodSecurityContext podSecurityContext,
        @Property(documentation = "`security` of the main container.", defaultValue = "null")
                ContainerSecurityContext containerSecurityContext,
        @Property(documentation = "`ttlSecondsAfterFinished` of the `Job`", defaultValue = "null")
                Integer ttlSecondsAfterFinished,
        @Property(
                        documentation =
                                "`activeDeadlineSeconds` of the `Job`. Note that a benchmark can't last longer that this duration. Do not forget to override it for long jobs and adjust orchestrator duration accordingly.",
                        defaultValue = "null")
                Integer activeDeadlineSeconds,
        @Property(documentation = "Resources.", defaultValue = "null") Resources resources,
        @Property(documentation = "Overrides default image (if set) of the main container.", defaultValue = "null")
                String image,
        @Property(
                        documentation = "Overrides default image pull policy if set of the main container.",
                        defaultValue = "null")
                String imagePullPolicy,
        @Property(documentation = "Image pull secret if the repository is private.", defaultValue = "null")
                List<ImagePullSecret> imagePullSecrets,
        @Property(
                        documentation = "Init containers executed before the actual default runtime (if any).",
                        defaultValue = "null")
                List<InitContainer> initContainers,
        @Property(documentation = "Global `Job` labels (and pod labels if not overridden).", defaultValue = "null")
                Map<String, String> labels,
        @Property(
                        documentation = "Global `Job` annotations (and pod annotations if not overridden).",
                        defaultValue = "null")
                Map<String, String> annotations,
        @Property(documentation = "Specific pod labels.", defaultValue = "null") Map<String, String> podLabels,
        @Property(documentation = "Specific pod annotations.", defaultValue = "null")
                Map<String, String> podAnnotations) {}
