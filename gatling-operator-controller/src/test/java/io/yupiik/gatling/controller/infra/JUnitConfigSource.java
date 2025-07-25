package io.yupiik.gatling.controller.infra;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.extension.ExtensionContext;

@DefaultScoped
public class JUnitConfigSource implements ConfigurationSource {
    private final ExtensionContext context;

    public JUnitConfigSource(final ExtensionContext context) {
        this.context = context;
    }

    @Override
    public String get(final String key) {
        return switch (key) {
            case "operator.kubernetes.master" -> context.getStore(KubernetesServerExtension.KEY)
                    .get(Kubernetes.class, Kubernetes.class)
                    .base();
            case "operator.kubernetes.token" -> "junit";
            case "operator.use-bookmarks" -> "false";
            case "operator.probe-port" -> "-1";
            case "operator.await" -> "false";
            default -> null;
        };
    }
}
