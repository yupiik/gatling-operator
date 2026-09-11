package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@RootConfiguration("do-generate-report")
public record GatlingGenerateReportTriggerCommandConfiguration(
        @Property(
                        value = "reporter-endpoint",
                        documentation =
                                "Endpoint of `start-report-server` server to call to trigger the report generation.",
                        defaultValue = "\"http://reporter:8080/api/end\"")
                String endpoint,
        @Property(
                        value = "reporter-timeout",
                        documentation = "Max duration the report generation can be awaited.",
                        defaultValue = "600_000L")
                long timeout) {}
