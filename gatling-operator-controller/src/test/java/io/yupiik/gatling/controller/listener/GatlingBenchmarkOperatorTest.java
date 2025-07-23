package io.yupiik.gatling.controller.listener;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import io.yupiik.gatling.controller.infra.Kubernetes;
import io.yupiik.gatling.controller.infra.OperatorSupport;
import io.yupiik.gatling.controller.version.VersionHolder;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

@OperatorSupport
class GatlingBenchmarkOperatorTest {
    @Test
    void create(final Kubernetes kubernetes) {
        kubernetes.sendEvent(
                """
                {
                  "type": "ADDED",
                  "object": {
                    "apiVersion": "gatling.yupiik.io/v1",
                    "kind": "GatlingBenchmark",
                    "metadata": {
                      "name": "bench-1",
                      "namespace": "junit"
                    },
                    "spec": {
                    }
                  }
                }""");
        final var request = kubernetes.requests(1);
        assertEquals(
                """
                        POST /api/v1/namespaces/junit/jobs?fieldManager=kubectl-client-side-apply&fieldValidation=Strict
                        {
                          "apiVersion": "batch/v1",
                          "kind": "Job",
                          "metadata": {
                            "name": "bench-1-19700101000000",
                            "labels": {
                              "gatling.yupiik.io/parent-name": "bench-1"
                            },
                            "annotations": {}
                          },
                          "spec": {
                            "backoffLimit": 0,
                            "ttlSecondsAfterFinished": 300,
                            "template": {
                              "metadata": {
                                "labels": {
                                  "gatling.yupiik.io/parent-name": "bench-1"
                                },
                                "annotations": {}
                              },
                              "spec": {
                                "activeDeadlineSeconds": 6000,
                                "containers": [
                                  {
                                    "args": [],
                                    "image": "yupiik/gatling-cli:latest",
                                    "imagePullPolicy": "Always",
                                    "workingDir": "/tmp",
                                    "name": "orchestrator",
                                    "securityContext": {
                                      "fsGroup": 10000,
                                      "fsGroupChangePolicy": "OnRootMismatch",
                                      "seccompProfile": {
                                        "type": "RuntimeDefault"
                                      }
                                    },
                                    "env": [
                                      {
                                        "name": "K8S_POD_IP",
                                        "valueFrom": {
                                          "fieldRef": {
                                            "fieldPath": "status.podIP"
                                          }
                                        }
                                      }
                                    ],
                                    "resources": {
                                      "requests": {
                                        "cpu": "100m",
                                        "memory": "128Mi"
                                      },
                                      "limits": {
                                        "memory": "128Mi"
                                      }
                                    }
                                  }
                                ],
                                "initContainers": [],
                                "imagePullSecrets": [],
                                "automountServiceAccountToken": true,
                                "serviceAccountName": "gatling-orchestrator",
                                "securityContext": {
                                  "capabilities": {
                                    "drop": [
                                      "ALL"
                                    ]
                                  },
                                  "readOnlyRootFilesystem": true,
                                  "runAsNonRoot": true,
                                  "runAsUser": 10000,
                                  "allowPrivilegeEscalation": false
                                },
                                "dnsConfig": {
                                  "options": [
                                    {
                                      "name": "ndots",
                                      "value": "2"
                                    }
                                  ]
                                },
                                "restartPolicy": "Never",
                                "nodeSelector": {},
                                "affinity": {},
                                "tolerations": []
                              }
                            }
                          }
                        }"""
                        .replace(
                                "\"image\": \"yupiik/gatling-cli:latest\",",
                                "\"image\": \"yupiik/gatling-cli:" + VersionHolder.VERSION.toLowerCase(Locale.ROOT)
                                        + "\",")
                        .replace(
                                "\"imagePullPolicy\": \"Always\",",
                                "\"imagePullPolicy\": \""
                                        + (VersionHolder.VERSION.endsWith("-SNAPSHOT") ? "Always" : "IfNotPresent")
                                        + "\","),
                request.iterator().next().asString());
    }

    @Test
    void delete(final Kubernetes kubernetes) {
        kubernetes.handle(
                new Kubernetes.CrdHandler() {
                    @Override
                    protected boolean doGet(final HttpExchange exchange) throws IOException {
                        if (exchange.getRequestURI().getPath().equals("/api/v1/namespaces/junit/jobs")) {
                            send(
                                    exchange,
                                    200,
                                    // limit=xxx&labelSelector=xxx, we ignore label in the response since we do not use
                                    // them
                                    """
                                            {
                                              "items": [
                                                {"metadata":{"name":"j1"}},
                                                {"metadata":{"name":"j2"}}
                                              ]
                                            }
                                            """);
                            return false;
                        }
                        return super.doGet(exchange);
                    }
                },
                () -> {
                    kubernetes.sendEvent(
                            """
                            {
                              "type": "DELETED",
                              "object": {
                                "apiVersion": "gatling.yupiik.io/v1",
                                "kind": "GatlingBenchmark",
                                "metadata": {
                                  "name": "bench-1",
                                  "namespace": "junit"
                                },
                                "spec": {
                                }
                              }
                            }""");
                    kubernetes.requests(3);
                });
        final var request = kubernetes.requests(0);
        assertEquals(
                """
                        GET /api/v1/namespaces/junit/jobs?limit=500&labelSelector=gatling.yupiik.io/parent-name=bench-1

                        DELETE /api/v1/namespaces/junit/jobs/j1
                        {
                          "kind": "DeleteOptions",
                          "apiVersion": "v1",
                          "propagationPolicy": "Background",
                          "gracePeriodSeconds": 60
                        }
                        DELETE /api/v1/namespaces/junit/jobs/j2
                        {
                          "kind": "DeleteOptions",
                          "apiVersion": "v1",
                          "propagationPolicy": "Background",
                          "gracePeriodSeconds": 60
                        }""",
                request.stream().map(Kubernetes.Request::asString).collect(joining("\n")));
    }
}
