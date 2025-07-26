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
                        defaultValue = "java.util.List.of()")
                List<Alveolus> pipeline,
        @Property(
                        value = "auto-clean",
                        documentation = "Should the CRD be deleted after the execution.",
                        defaultValue = "false")
                boolean autoClean) {
    @JsonModel
    public record Alveolus(
            @Property(documentation = "Alveolus name.", required = true) String name,
            @Property(
                            documentation =
                                    "Placeholders for the alveolus. " + "An implicit placeholders"
                                            + " `gatling-operator.implicit.range` is set to range value and"
                                            + " `gatling-operator.implicit.index` is set to the index within the range (`0` for the first element of the pipeline with the range `R` for example). This enables to duplicate the alveoli to scale - to have N instances of gatling for example and change the user profile depending the instance.",
                            defaultValue = "java.util.Map.of()")
                    Map<String, String> placeholders,
            @Property(
                            documentation =
                                    "_Range_ of the alveolus, alveoli with the same range are executed concurrently. A negative range means _start, await started and continue then delete when all the pipeline is executed.",
                            defaultValue = "0")
                    int range) {}
}
