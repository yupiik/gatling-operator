package io.yupiik.gatling.controller.listener;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import io.yupiik.gatling.controller.infra.Kubernetes;
import io.yupiik.gatling.controller.infra.OperatorSupport;
import io.yupiik.gatling.controller.version.VersionHolder;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@OperatorSupport
class GatlingBenchmarkOperatorTest {
    @Test
    void create(final Kubernetes kubernetes) {
        kubernetes.handle(
                new Kubernetes.CrdHandler() {
                    @Override
                    protected boolean doGet(final HttpExchange exchange) throws IOException {
                        if (exchange.getRequestURI()
                                .getPath()
                                .equals("/apis/batch/v1/namespaces/default/jobs/bench-1-19700101000000")) {
                            send(
                                    exchange,
                                    200,
                                    """
                                    {
                                      "status": {
                                        "succeeded": 1
                                      }
                                    }""");
                            return false;
                        }
                        return super.doGet(exchange);
                    }
                },
                () -> {
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
                    final var request = kubernetes.requests(4).getFirst();
                    assertEquals(
                            "POST /apis/batch/v1/namespaces/default/jobs?fieldManager=kubectl-client-side-apply&fieldValidation=Strict",
                            request.requestLine());
                    request.assertJsonPayloadEquals(
                            """
                            {
                              "apiVersion": "batch/v1",
                              "kind": "Job",
                              "metadata": {
                                "name": "bench-1-19700101000000",
                                "labels": {
                                  "gatling.yupiik.io/parent-name": "bench-1",
                                  "gatling.yupiik.io/started-timestamp": "0"
                                },
                                "annotations": {}
                              },
                              "spec": {
                                "backoffLimit": 0,
                                "ttlSecondsAfterFinished": 300,
                                "template": {
                                  "metadata": {
                                    "labels": {
                                      "gatling.yupiik.io/parent-name": "bench-1",
                                      "gatling.yupiik.io/started-timestamp": "0"
                                    },
                                    "annotations": {}
                                  },
                                  "spec": {
                                    "activeDeadlineSeconds": 6000,
                                    "containers": [
                                      {
                                        "args": [
                                          "bench",
                                          "--benchmark-base-uri",
                                          "https://kubernetes.api/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/bench-1",
                                          "--spec-pipeline-length",
                                          "0"
                                        ],
                                        "image": "yupiik/gatling-cli:latest",
                                        "imagePullPolicy": "Always",
                                        "workingDir": "/tmp",
                                        "name": "orchestrator",
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
                                        "env": [
                                          {
                                            "valueFrom": {
                                              "fieldRef": {
                                                "fieldPath": "status.podIP"
                                              }
                                            },
                                            "name": "K8S_POD_IP"
                                          },
                                          {
                                            "value": "-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75 -Djdk.httpclient.keepalive.timeout=30 -Dsun.net.inetaddr.ttl=60 -Dio.yupiik.logging.jul.handler.AsyncHandler.formatter=json",
                                            "name": "_JAVA_OPTIONS"
                                          }
                                        ],
                                        "command": [
                                          "java",
                                          "-XX:+ExitOnOutOfMemoryError",
                                          "-XX:MaxRAMPercentage=75",
                                          "-Djdk.httpclient.keepalive.timeout=30",
                                          "-Dsun.net.inetaddr.ttl=60",
                                          "-Djava.util.logging.manager=io.yupiik.logging.jul.YupiikLogManager",
                                          "-Dio.yupiik.logging.jul.handler.AsyncHandler.formatter=json",
                                          "-cp",
                                          "@/opt/yupiik/gatling-operator/gatling-cli/jib-classpath-file",
                                          "io.yupiik.fusion.framework.api.main.Launcher"
                                        ],
                                        "volumeMounts": [],
                                        "resources": {
                                          "requests": {
                                            "cpu": "100m",
                                            "memory": "256Mi"
                                          },
                                          "limits": {
                                            "memory": "256Mi"
                                          }
                                        }
                                      }
                                    ],
                                    "initContainers": [],
                                    "imagePullSecrets": [],
                                    "automountServiceAccountToken": true,
                                    "serviceAccountName": "gatling-orchestrator",
                                    "securityContext": {
                                      "fsGroup": 10000,
                                      "fsGroupChangePolicy": "OnRootMismatch",
                                      "seccompProfile": {
                                        "type": "RuntimeDefault"
                                      }
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
                                    "tolerations": [],
                                    "volumes": []
                                  }
                                }
                              }
                            }"""
                                    .replace(
                                            "\"image\": \"yupiik/gatling-cli:latest\",",
                                            "\"image\": \"yupiik/gatling-cli:" + VersionHolder.VERSION + "\",")
                                    .replace(
                                            "\"imagePullPolicy\": \"Always\",",
                                            "\"imagePullPolicy\": \""
                                                    + (VersionHolder.VERSION.endsWith("-SNAPSHOT")
                                                            ? "Always"
                                                            : "IfNotPresent")
                                                    + "\","));
                });
    }

    @Test
    void delete(final Kubernetes kubernetes) {
        kubernetes.handle(
                new Kubernetes.CrdHandler() {
                    @Override
                    protected boolean doGet(final HttpExchange exchange) throws IOException {
                        if (exchange.getRequestURI().getPath().equals("/apis/batch/v1/namespaces/default/jobs")) {
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
                        if (exchange.getRequestURI().getPath().equals("/api/v1/namespaces/default/services")) {
                            send(
                                    exchange,
                                    200,
                                    """
                                            {
                                              "items": []
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
                    kubernetes.requests(4);
                });
        final var request = kubernetes.requests(0);
        assertEquals(
                """
                        DELETE /apis/batch/v1/namespaces/default/jobs/j1
                        {
                          "kind": "DeleteOptions",
                          "apiVersion": "v1",
                          "propagationPolicy": "Background",
                          "gracePeriodSeconds": 60
                        }
                        DELETE /apis/batch/v1/namespaces/default/jobs/j2
                        {
                          "kind": "DeleteOptions",
                          "apiVersion": "v1",
                          "propagationPolicy": "Background",
                          "gracePeriodSeconds": 60
                        }
                        GET /api/v1/namespaces/default/services?limit=500&labelSelector=gatling.yupiik.io/parent-name=bench-1

                        GET /apis/batch/v1/namespaces/default/jobs?limit=500&labelSelector=gatling.yupiik.io/parent-name=bench-1
                        """,
                request.stream().map(Kubernetes.Request::asString).sorted().collect(joining("\n")));
    }
}
