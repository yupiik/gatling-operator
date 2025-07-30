package io.yupiik.gatling.orchestrator.command.gatling;

import io.gatling.charts.stats.LogFileData;
import io.gatling.charts.stats.LogFileReader;
import io.gatling.core.config.GatlingConfiguration;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import java.nio.file.Path;

@DefaultScoped
public class GatlingReportLoader {
    private final GatlingConfiguration configuration = GatlingConfiguration.load();

    public LogFileData load(final Path simulationLog) {
        final var abs = simulationLog.toAbsolutePath();
        return LogFileReader.apply(
                        abs.getParent().getFileName().toString(),
                        abs.getParent().getParent(),
                        GatlingConfiguration.load())
                .read();
    }
}
