package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;

@ApplicationScoped
@Command(name = "bench", description = "Run a benchmark based on the provided configuration.")
public class BenchmarkCommand implements Runnable {
    private final BenchmarkCommandConfiguration configuration;

    public BenchmarkCommand(final BenchmarkCommandConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        // todo
    }
}
