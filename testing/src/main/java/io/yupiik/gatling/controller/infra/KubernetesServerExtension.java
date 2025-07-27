package io.yupiik.gatling.controller.infra;

import static java.util.Optional.ofNullable;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class KubernetesServerExtension
        implements BeforeAllCallback, AfterAllCallback, ParameterResolver, BeforeEachCallback {
    static final ExtensionContext.Namespace KEY = ExtensionContext.Namespace.create(Kubernetes.class);

    @Override
    public void beforeAll(final ExtensionContext context) {
        context.getStore(KEY).put(Kubernetes.class, new Kubernetes());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        ofNullable(context.getStore(KEY).get(Kubernetes.class, Kubernetes.class))
                .ifPresent(Kubernetes::close);
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Kubernetes.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return extensionContext.getStore(KEY).get(Kubernetes.class, Kubernetes.class);
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        context.getStore(KEY)
                .get(Kubernetes.class, Kubernetes.class)
                .requests(0)
                .clear();
    }
}
