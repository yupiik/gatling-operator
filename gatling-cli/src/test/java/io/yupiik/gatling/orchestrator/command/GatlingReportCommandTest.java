package io.yupiik.gatling.orchestrator.command;

import static io.yupiik.gatling.orchestrator.command.infra.CliRunner.cli;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GatlingReportCommandTest {
    @Test
    void sendReports(@TempDir final Path reports) throws IOException {
        // generate some reports (fake to make asserts easier)
        final var runId = Files.createDirectory(reports.resolve("sample-20250729211636221"));
        final var simulationLog = runId.resolve("simulation.log");
        Files.writeString(simulationLog, "this is a test");

        // run the command against a mock server
        final var captures = new CopyOnWriteArrayList<String>();
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 8);
        server.createContext("/").setHandler(ex -> {
            try (ex) {
                if ("POST".equals(ex.getRequestMethod())
                        && "/api/reports".equals(ex.getRequestURI().getPath())) {
                    try (final var in = ex.getRequestBody()) {
                        captures.add(new String(in.readAllBytes(), UTF_8));
                    }
                    ex.sendResponseHeaders(200, 2);
                    ex.getResponseBody().write('{');
                    ex.getResponseBody().write('}');
                } else {
                    ex.sendResponseHeaders(404, 00);
                }
            }
        });
        try {
            server.start();
            cli(
                    "gatling-report",
                    "--reporter-endpoint",
                    "http://localhost:" + server.getAddress().getPort() + "/api/reports",
                    "--report-directory",
                    reports.toString());
        } finally {
            server.stop(0);
        }
        assertEquals(List.of("this is a test"), captures);
    }
}
