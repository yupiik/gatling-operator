package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@RootConfiguration("gatling-report")
public record GatlingReportCommandConfiguration(
        @Property(
                        value = "report-directory",
                        documentation = "Where reports are generated to.",
                        defaultValue = "\"/gatling/reports\"")
                String reportDirectory,
        @Property(
                        value = "reporter-endpoint",
                        documentation =
                                "Where to send reports to. Ensure to adjust it using placeholders to the `start-report-server` job name.",
                        defaultValue = "\"http://reporter:8080/api/reports\"")
                String reporterEndpoint,
        @Property(
                        value = "reporter-timeout",
                        documentation = "Timeout to send a report in ms.",
                        defaultValue = "60_000L")
                long timeout) {}
