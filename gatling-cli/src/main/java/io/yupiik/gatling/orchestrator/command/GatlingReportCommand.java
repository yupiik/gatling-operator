package io.yupiik.gatling.orchestrator.command;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;

@DefaultScoped
@Command(
        name = "gatling-report",
        description = "Retrieve gatling pod `simulation.log`, concatenate it and generate a report. "
                + "It does behave as a `tail` on the `simulation.log` file and copies it locally.")
public class GatlingReportCommand implements Runnable {
    @Override
    public void run() {

        // todo: run
        // https://github.com/gatling/gatling/blob/main/gatling-app/src/main/scala/io/gatling/app/RunResultProcessor.scala#L28
    }
}
