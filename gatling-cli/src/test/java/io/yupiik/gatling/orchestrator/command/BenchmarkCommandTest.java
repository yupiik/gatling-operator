package io.yupiik.gatling.orchestrator.command;

import static io.yupiik.gatling.orchestrator.command.infra.CliRunner.cli;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import io.yupiik.gatling.controller.infra.Kubernetes;
import io.yupiik.gatling.controller.infra.KubernetesServerExtension;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KubernetesServerExtension.class)
class BenchmarkCommandTest {
    @Test
    void run(final Kubernetes kubernetes) {
        kubernetes.handle(
                new Kubernetes.CrdHandler() {
                    @Override
                    protected boolean doGet(final HttpExchange exchange) throws IOException {
                        if (exchange.getRequestURI()
                                .getPath()
                                .startsWith("/apis/batch/v1/namespaces/default/jobs/my-bench-")) {
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
                    cli(
                            kubernetes,
                            "bench",
                            "--benchmark-base-uri",
                            "https://kubernetes.api/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/my-bench",
                            "--spec-auto-clean",
                            "true",
                            "--spec-pipeline-length",
                            "3",
                            "--spec-pipeline-0-name",
                            "gatling-operator#generic-job#fire-and-forget",
                            "--spec-pipeline-0-range",
                            "0",
                            "--spec-pipeline-0-placeholders",
                            """
                                    users=100
                                    duration=100""",
                            "--spec-pipeline-1-name",
                            "gatling-operator#generic-job#awaited",
                            "--spec-pipeline-1-range",
                            "1",
                            "--spec-pipeline-1-placeholders",
                            """
                                    generic-job.command=["java","..."]
                                    generic-job.image=gatling:latest""",
                            "--spec-pipeline-2-name",
                            "gatling-operator#generic-job#awaited",
                            "--spec-pipeline-2-range",
                            "1",
                            "--spec-pipeline-2-placeholders",
                            """
                                    generic-job.command=["node"]
                                    generic-job.args=["..."]
                                    generic-job.image=node:latest""");
                    assertEquals(
                            Set.of(
                                    new Kubernetes.Request(
                                            "PATCH",
                                            URI.create(
                                                    "/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/my-bench/status"),
                                            """
                                                    {
                                                      "status": {
                                                        "range": -1,
                                                        "status": "RUNNING"
                                                      }
                                                    }"""),
                                    new Kubernetes.Request(
                                            "PATCH",
                                            URI.create(
                                                    "/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/my-bench/status"),
                                            """
                                                    {
                                                      "status": {
                                                        "range": 0,
                                                        "status": "RUNNING"
                                                      }
                                                    }"""),
                                    new Kubernetes.Request("GET", URI.create("/api/v1"), ""),
                                    new Kubernetes.Request("GET", URI.create("/apis/batch/v1"), ""),
                                    new Kubernetes.Request(
                                            "GET",
                                            URI.create("/apis/batch/v1/namespaces/default/jobs/my-bench-0-0"),
                                            ""),
                                    new Kubernetes.Request(
                                            "GET",
                                            URI.create("/apis/batch/v1/namespaces/default/jobs/my-bench-1-0"),
                                            ""),
                                    new Kubernetes.Request(
                                            "GET",
                                            URI.create("/apis/batch/v1/namespaces/default/jobs/my-bench-1-1"),
                                            ""),
                                    new Kubernetes.Request(
                                            "POST",
                                            URI.create(
                                                    "/apis/batch/v1/namespaces/default/jobs?fieldManager=kubectl-client-side-apply&fieldValidation=Strict"),
                                            """
                                                    {
                                                      "apiVersion": "batch/v1",
                                                      "kind": "Job",
                                                      "metadata": {
                                                        "name": "my-bench-0-0",
                                                        "labels": {
                                                          "gatling.yupiik.io/parent-name": "my-bench"
                                                        },
                                                        "annotations": {}
                                                      },
                                                      "spec": {
                                                        "backoffLimit": 0,
                                                        "ttlSecondsAfterFinished": 300,
                                                        "template": {
                                                          "metadata": {
                                                            "labels": {
                                                              "gatling.yupiik.io/parent-name": "my-bench"
                                                            },
                                                            "annotations": {}
                                                          },
                                                          "spec": {
                                                            "activeDeadlineSeconds": 14400,
                                                            "containers": [
                                                              {
                                                                "image": "yupiik/gatling-cli:latest",
                                                                "imagePullPolicy": "Always",
                                                                "workingDir": "/tmp",
                                                                "name": "orchestrator",
                                                                "resources": {
                                                                  "requests": {
                                                                    "cpu": "100m",
                                                                    "memory": "256Mi"
                                                                  },
                                                                  "limits": {
                                                                    "memory": "256Mi"
                                                                  }
                                                                },
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
                                                                "env": [],
                                                                "volumeMounts": []
                                                              }
                                                            ],
                                                            "initContainers": [],
                                                            "imagePullSecrets": [],
                                                            "automountServiceAccountToken": true,
                                                            "serviceAccountName": "default",
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
                                                    }"""),
                                    new Kubernetes.Request(
                                            "PATCH",
                                            URI.create(
                                                    "/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/my-bench/status"),
                                            """
                                                    {
                                                      "status": {
                                                        "range": 1,
                                                        "status": "RUNNING"
                                                      }
                                                    }"""),
                                    new Kubernetes.Request(
                                            "POST",
                                            URI.create(
                                                    "/apis/batch/v1/namespaces/default/jobs?fieldManager=kubectl-client-side-apply&fieldValidation=Strict"),
                                            """
                                                    {
                                                      "apiVersion": "batch/v1",
                                                      "kind": "Job",
                                                      "metadata": {
                                                        "name": "my-bench-1-0",
                                                        "labels": {
                                                          "gatling.yupiik.io/parent-name": "my-bench"
                                                        },
                                                        "annotations": {}
                                                      },
                                                      "spec": {
                                                        "backoffLimit": 0,
                                                        "ttlSecondsAfterFinished": 300,
                                                        "template": {
                                                          "metadata": {
                                                            "labels": {
                                                              "gatling.yupiik.io/parent-name": "my-bench"
                                                            },
                                                            "annotations": {}
                                                          },
                                                          "spec": {
                                                            "activeDeadlineSeconds": 14400,
                                                            "containers": [
                                                              {
                                                                "image": "gatling:latest",
                                                                "imagePullPolicy": "Always",
                                                                "workingDir": "/tmp",
                                                                "name": "orchestrator",
                                                                "resources": {
                                                                  "requests": {
                                                                    "cpu": "100m",
                                                                    "memory": "256Mi"
                                                                  },
                                                                  "limits": {
                                                                    "memory": "256Mi"
                                                                  }
                                                                },
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
                                                                "env": [],
                                                                "command": [
                                                                  "java",
                                                                  "..."
                                                                ],
                                                                "volumeMounts": []
                                                              }
                                                            ],
                                                            "initContainers": [],
                                                            "imagePullSecrets": [],
                                                            "automountServiceAccountToken": true,
                                                            "serviceAccountName": "default",
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
                                                    }"""),
                                    new Kubernetes.Request(
                                            "POST",
                                            URI.create(
                                                    "/apis/batch/v1/namespaces/default/jobs?fieldManager=kubectl-client-side-apply&fieldValidation=Strict"),
                                            """
                                                    {
                                                      "apiVersion": "batch/v1",
                                                      "kind": "Job",
                                                      "metadata": {
                                                        "name": "my-bench-1-1",
                                                        "labels": {
                                                          "gatling.yupiik.io/parent-name": "my-bench"
                                                        },
                                                        "annotations": {}
                                                      },
                                                      "spec": {
                                                        "backoffLimit": 0,
                                                        "ttlSecondsAfterFinished": 300,
                                                        "template": {
                                                          "metadata": {
                                                            "labels": {
                                                              "gatling.yupiik.io/parent-name": "my-bench"
                                                            },
                                                            "annotations": {}
                                                          },
                                                          "spec": {
                                                            "activeDeadlineSeconds": 14400,
                                                            "containers": [
                                                              {
                                                                "args": [
                                                                  "..."
                                                                ],
                                                                "image": "node:latest",
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
                                                                "env": [],
                                                                "command": [
                                                                  "node"
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
                                                            "serviceAccountName": "default",
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
                                                    }"""),
                                    new Kubernetes.Request(
                                            "DELETE",
                                            URI.create(
                                                    "/apis/gatling.yupiik.io/v1/namespaces/default/gatlingbenchmarks/my-bench"),
                                            """
                                                    {
                                                      "kind": "DeleteOptions",
                                                      "apiVersion": "v1",
                                                      "propagationPolicy": "Foreground",
                                                      "gracePeriodSeconds": 0
                                                    }""")),
                            new HashSet<>(kubernetes.requests(12)));
                });
    }
}
