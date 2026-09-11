package io.yupiik.gatling.orchestrator.command;

import static io.yupiik.gatling.orchestrator.command.infra.CliRunner.cli;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class GatlingGenerateReportTriggerCommandTest {
    @Test
    void trigger() throws IOException {
        final var captures = new CopyOnWriteArrayList<String>();
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 8);
        server.createContext("/").setHandler(ex -> {
            final var head = "HEAD".equals(ex.getRequestMethod())
                    && "/api/end".equals(ex.getRequestURI().getPath());
            try (ex) {
                if (head) {
                    captures.add("ok");
                    ex.sendResponseHeaders(200, 0);
                } else {
                    ex.sendResponseHeaders(404, 0);
                }
            } finally {
                if (head) {
                    server.stop(0);
                }
            }
        });
        try {
            server.start();
            cli(
                    "do-generate-report",
                    "--reporter-endpoint",
                    "http://localhost:" + server.getAddress().getPort() + "/api/end");
        } finally {
            server.stop(0);
        }
        assertEquals(List.of("ok"), captures);
    }
}
