package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@RootConfiguration("start-report-server")
public record GatlingGlobalReportCommandConfiguration(
        @Property(value = "working-directory", documentation = "Where to store temporary binary report files")
                String workDirectory,
        @Property(
                        value = "report-directory",
                        documentation = "Where to write the report (named report.adoc by convention today)")
                String reportDirectory,
        @Property(value = "port", documentation = "Port to bind the server to") int port) {}
