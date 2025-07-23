package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.Map;

@JsonModel
public record Resources(Map<String, String> requests, Map<String, String> limits) {
    public static boolean isEmpty(final Resources resources) {
        return resources == null
                || ((resources.limits() == null || resources.limits().isEmpty())
                        && (resources.requests() == null || resources.requests().isEmpty()));
    }
}
