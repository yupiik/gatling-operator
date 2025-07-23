package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;

@JsonModel
public record Capabilities(List<String> add, List<String> drop) {
    public static boolean isEmpty(final Capabilities capabilities) {
        return capabilities == null
                || ((capabilities.add() == null || capabilities.add().isEmpty())
                        && (capabilities.drop() == null || capabilities.drop().isEmpty()));
    }
}
