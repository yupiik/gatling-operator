package io.yupiik.gatling.kubernetes.model.operator;

import io.yupiik.fusion.framework.build.api.configuration.ConfigurationModel;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;
import java.util.Map;

@JsonModel
@ConfigurationModel // to have defaults and doc when embed in another module
public record GatlingBenchmarkSpec(
        @Property(
                        documentation =
                                "Steps to execute. Default available alveolus is `gatling-operator#generic-job#awaited` - ensure to configure its placeholders to make it effective.",
                        defaultValue =
                                "java.util.List.<io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkSpec.Alveolus>of()")
                List<Alveolus> pipeline,
        @Property(
                        value = "auto-clean",
                        documentation =
                                "Should the CRD be deleted after the execution. "
                                        + "It works by fetching the jobs and services with the label `gatling.yupiik.io/parent-name` equals to the CRD (this enclosing descriptor) name so ensure to adjust your labels in placeholders. "
                                        + "You can use implicit placeholders for that since it provides your the value as a _variable_ so you do not need to hardcode it (useful when combined with a CI/CD and/or a `CronJob`).")
                Boolean autoClean,
        @Property( // 4h
                        value = "timeout",
                        documentation = "Max duration of the full pipeline in milliseconds.",
                        defaultValue = "14_400_000L")
                Long timeout) {
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
                                            + " `gatling-operator.implicit.version` is set to operator (and CLI) value. "
                                            + " `gatling-operator.implicit.index` is set to the index within the range (`0` for the first element of the pipeline with the range `R` for example). This enables to duplicate the alveoli to scale - to have N instances of gatling for example and change the user profile depending the instance. "
                                            + "Note that some are set directly in the operator (parent name for example) but some need lazy evaluation in the orchestrator, for these ones you need to escape the value otherwise it would be evaluated too early (use an anti-slash).")
                    Map<String, String> placeholders,
            @Property(
                            documentation =
                                    "_Range_ of the alveolus, alveoli with the same range are executed concurrently. A negative range means _start, await started and continue then delete when all the pipeline is executed.",
                            defaultValue = "0")
                    Integer range,
            @Property(
                            documentation =
                                    "Range the job (only works for jobs and services) can be killed. For example if you use `gatling-operator#generic-service#fire-and-forget` at range `0` and run the injectors at range `1`, it means at range `2` you can kill this pod so setting `2` will avoid it to leak if you do not use `autoClean`.",
                            defaultValue = "null")
                    Integer deleteRange,
            @Property(
                            documentation = "Should the step be executed if a previous range failed or not.",
                            defaultValue =
                                    "io.yupiik.gatling.kubernetes.model.operator.GatlingBenchmarkSpec.ExecuteCondition.ON_PREVIOUS_SUCCESS")
                    ExecuteCondition executeCondition,
            @Property( // 4h
                            value = "timeout",
                            documentation =
                                    "Max duration of the deployment (or execution if the await condition awaits the end of the job). Ignored if no await condition are set but also means the job will be deployed and forgotten.",
                            defaultValue = "14_400_000L")
                    Long timeout) {}

    @JsonModel
    public enum ExecuteCondition {
        ALWAYS,
        ON_PREVIOUS_SUCCESS
    }
}
