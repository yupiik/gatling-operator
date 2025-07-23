package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@ApplicationScoped
@Command(name = "bench", description = "Run a benchmark based on the provided configuration.")
public class BenchmarkCommands implements Runnable {
    private final Conf configuration;

    public BenchmarkCommands(final Conf configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        // todo
    }

    @RootConfiguration("bench")
    public record Conf(@Property(documentation = "The main name.") String name) {}
}
