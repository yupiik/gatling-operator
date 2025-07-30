package io.yupiik.gatling.controller.infra;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import java.util.Optional;

@DefaultScoped
public class JUnitConfigSource implements ConfigurationSource {
    private final Kubernetes context;

    public JUnitConfigSource(final Optional<Kubernetes> context) {
        this.context = context.orElse(null);
    }

    @Override
    public String get(final String key) {
        return switch (key) {
            case "operator.kubernetes.master" -> context.base();
            case "operator.kubernetes.token" -> "junit";
            case "operator.use-bookmarks" -> "false";
            case "operator.probe-port" -> "-1";
            case "operator.await" -> "false";
            default -> null;
        };
    }
}
