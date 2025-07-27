package io.yupiik.gatling.kubernetes.model;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;
import java.util.Map;

@JsonModel
public record GatlingBenchmarkSpec(
        @Property(
                        documentation =
                                "Steps to execute. Default available alveolus is `gatling-operator#injector` - ensure to configure its placeholders to make it effective.",
                        defaultValue =
                                "java.util.List.<io.yupiik.gatling.kubernetes.model.GatlingBenchmarkSpec.Alveolus>of()")
                List<Alveolus> pipeline,
        @Property(
                        value = "auto-clean",
                        documentation = "Should the CRD be deleted after the execution.",
                        defaultValue = "false")
                boolean autoClean,
        @Property( // 4h
                        value = "timeout",
                        documentation = "Max duration of the full pipeline in milliseconds.",
                        defaultValue = "14_400_000L")
                long timeout) {
    @JsonModel
    public record Alveolus(
            @Property(
                            documentation =
                                    "Alveolus name - generally a `Job`. " + "See xref:advanced.adoc[advanced] part. "
                                            + "Important: ensure to configure bundlebee `awaitConditions` in your alveolus to have the proper behavior (start, await running and continue for example OR start, await finished - `Complete` or `Failed`.",
                            required = true)
                    String name,
            @Property(
                            documentation =
                                    "Placeholders for the alveolus. Some implicit placeholders are set. "
                                            + " `generic-job.name` is set to a default, CRD specific name if you reuse `generic-job` alveoli. "
                                            + " `gatling-operator.implicit.orchestrator-ip` is set to the orchestrator ip. "
                                            + " `gatling-operator.implicit.parent-name` is set to the benchmark CRD name. "
                                            + " `gatling-operator.implicit.range` is set to range value. "
                                            + " `gatling-operator.implicit.index` is set to the index within the range (`0` for the first element of the pipeline with the range `R` for example). This enables to duplicate the alveoli to scale - to have N instances of gatling for example and change the user profile depending the instance.",
                            defaultValue = "java.util.Map.of()")
                    Map<String, String> placeholders,
            @Property(
                            documentation =
                                    "_Range_ of the alveolus, alveoli with the same range are executed concurrently. A negative range means _start, await started and continue then delete when all the pipeline is executed.",
                            defaultValue = "0")
                    int range,
            @Property( // 4h
                            value = "timeout",
                            documentation =
                                    "Max duration of the deployment (or execution if the await condition awaits the end of the job). Ignored if no await condition are set but also means the job will be deployed and forgotten.",
                            defaultValue = "14_400_000")
                    long timeout) {}
}
