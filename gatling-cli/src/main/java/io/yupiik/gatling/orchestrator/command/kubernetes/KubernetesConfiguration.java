package io.yupiik.gatling.orchestrator.command.kubernetes;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@RootConfiguration("kubernetes")
public record KubernetesConfiguration(
        @Property(documentation = "Connect and request timeout in milliseconds.", defaultValue = "60_000L")
                long timeout,
        @Property(
                        documentation = "The kubernetes API base URL",
                        defaultValue = "java.util.Optional.ofNullable(System.getenv(\"KUBERNETES_SERVICE_HOST\"))"
                                + ".map(host -> \"https://\" + host + ':' + java.util.Optional.ofNullable(System.getenv(\"KUBERNETES_SERVICE_PORT\")).orElse(\"443\"))"
                                + ".orElse(\"https://kubernetes.default.svc\")")
                String master,
        @Property(value = "tls-skip", documentation = "Should TLS validations be skipped.", defaultValue = "false")
                boolean skipTls,
        @Property(
                        documentation = "Kubernetes token (service account).",
                        defaultValue = "\"/var/run/secrets/kubernetes.io/serviceaccount/token\"")
                String token,
        @Property(
                        documentation = "Kubernetes certificate to connect to its API",
                        defaultValue = "\"/var/run/secrets/kubernetes.io/serviceaccount/ca.crt\"")
                String certificates) {}
