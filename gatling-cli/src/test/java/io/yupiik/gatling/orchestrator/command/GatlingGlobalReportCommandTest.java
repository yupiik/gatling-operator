package io.yupiik.gatling.orchestrator.command;

import static io.yupiik.gatling.orchestrator.command.infra.CliRunner.cli;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GatlingGlobalReportCommandTest {
    @Test
    void run(@TempDir final Path work) throws IOException, InterruptedException {
        final var report = Base64.getDecoder()
                .decode(
                        "AAAAAAYzLjE0LjMAAAAAGGlvLnl1cGlpay5nYXRsaW5nLlNhbXBsZQAAAAGYWAtb/QAAAAAAAAABAAAAC1BpenphIE9yZGVyAAAAAAACAAAAAAEAAAF/AQAAAAAAAAABAAAAC09yZGVyIFBpenphAAAAAYwAAAMDAAAAAAIAAAArc3RhdHVzLmZpbmQuaXMoMjAwKSwgYnV0IGFjdHVhbGx5IGZvdW5kIDQwMQACAAAAAAAAAAMXAgAAAAABAAAU/AEAAAAA/////wAAFP0AABYaAP////4CAAAAAAAAABYcAgAAAAABAAAohAIAAAAAAQAAKOgCAAAAAAEAAClMAQAAAAD/////AAAohQAAKaAA/////gIAAAAAAAAAKaECAAAAAAEAACmwAgAAAAABAAAqFQEAAAAA/////wAAKOkAACoXAP////4CAAAAAAAAACoYAQAAAAD/////AAApTQAAKnMA/////gIAAAAAAAAAKnQCAAAAAAEAACp5AQAAAAD/////AAApsQAAKtIA/////gIAAAAAAAAAKtMCAAAAAAEAACrdAgAAAAABAAArQQEAAAAA/////wAAKhYAACtBAP////4CAAAAAAAAACtDAQAAAAD/////AAAqegAAK5oA/////gIAAAAAAAAAK5wCAAAAAAEAACumAQAAAAD/////AAAq3gAAK/kA/////gIAAAAAAAAAK/sCAAAAAAEAACwKAQAAAAD/////AAArQgAALGEA/////gIAAAAAAAAALGMCAAAAAAEAACxrAQAAAAD/////AAArpwAALMcA/////gIAAAAAAAAALMgCAAAAAAEAACzQAQAAAAD/////AAAsCgAALSsA/////gIAAAAAAAAALS0CAAAAAAEAAC00AQAAAAD/////AAAsbAAALYsA/////gIAAAAAAAAALY0CAAAAAAEAAC2YAQAAAAD/////AAAs0AAALfQA/////gIAAAAAAAAALfYCAAAAAAEAAC38AgAAAAABAAAuYQEAAAAA/////wAALTUAAC5rAP////4CAAAAAAAAAC5sAgAAAAABAAAuxAEAAAAA/////wAALZkAAC7NAP////4CAAAAAAAAAC7OAgAAAAABAAAvKQEAAAAA/////wAALf0AAC8pAP////4CAAAAAAAAAC8pAQAAAAD/////AAAuYQAAL3wA/////gIAAAAAAAAAL30CAAAAAAEAAC+NAQAAAAD/////AAAuxQAAL+QA/////gIAAAAAAAAAL+UCAAAAAAEAAC/xAQAAAAD/////AAAvKQAAMEgA/////gIAAAAAAAAAMEkCAAAAAAEAADBVAQAAAAD/////AAAvjQAAMLYA/////gIAAAAAAQAAMLgCAAAAAAAAADC4AgAAAAABAAAxHAEAAAAA/////wAAL/IAADEiAP////4CAAAAAAAAADEjAgAAAAABAAAxgAEAAAAA/////wAAMFUAADGVAP////4CAAAAAAAAADGXAQAAAAD/////AAAwuAAAMdYA/////gIAAAAAAAAAMdcCAAAAAAEAADHlAgAAAAABAAAySwEAAAAA/////wAAMR0AADJdAP////4CAAAAAAAAADJfAgAAAAABAAAyrwEAAAAA/////wAAMYEAADLCAP////4CAAAAAAAAADLEAQAAAAD/////AAAx5QAAMwgA/////gIAAAAAAAAAMwkCAAAAAAEAADMTAgAAAAABAAAzdwEAAAAA/////wAAMksAADN8AP////4CAAAAAAAAADN9AgAAAAABAAAz3AEAAAAA/////wAAMq8AADPfAP////4CAAAAAAAAADPgAgAAAAABAAA0OwEAAAAA/////wAAMxMAADQ+AP////4CAAAAAAAAADQ/AQAAAAD/////AAAzeAAANJkA/////gIAAAAAAAAANJsCAAAAAAEAADSgAgAAAAABAAA1BAEAAAAA/////wAAM9wAADUJAP////4CAAAAAAAAADUKAQAAAAD/////AAA0PAAANV0A/////gIAAAAAAAAANV4CAAAAAAEAADVoAQAAAAD/////AAA0oAAANbkA/////gIAAAAAAAAANbsCAAAAAAEAADXNAQAAAAD/////AAA1BQAANiUA/////gIAAAAAAAAANicCAAAAAAEAADYwAQAAAAD/////AAA1aQAANo0A/////gIAAAAAAAAANo8CAAAAAAEAADaVAQAAAAD/////AAA1zQAANu0A/////gIAAAAAAAAANu4CAAAAAAEAADb5AQAAAAD/////AAA2MQAAN1wA/////gIAAAAAAQAAN10CAAAAAAAAADdeAQAAAAD/////AAA2lgAAN8AA/////gIAAAAAAAAAN8ECAAAAAAEAADfCAgAAAAABAAA4IwEAAAAA/////wAANvoAADglAP////4CAAAAAAAAADgmAQAAAAD/////AAA3XgAAOIEA/////gIAAAAAAAAAOIICAAAAAAEAADiIAQAAAAD/////AAA3wgAAOOEA/////gIAAAAAAAAAOOICAAAAAAEAADjsAgAAAAABAAA5UAEAAAAA/////wAAOCQAADlSAP////4CAAAAAAAAADlTAQAAAAD/////AAA4iAAAObIA/////gIAAAAAAAAAObQCAAAAAAEAADm0AgAAAAABAAA6GAEAAAAA/////wAAOOwAADocAP////4CAAAAAAAAADodAgAAAAABAAA6fAEAAAAA/////wAAOVAAADqBAP////4CAAAAAAAAADqCAQAAAAD/////AAA5tAAAOtIA/////gIAAAAAAAAAOtMCAAAAAAEAADrgAQAAAAD/////AAA6GAAAO0IA/////gIAAAAAAAAAO0MCAAAAAAEAADtEAQAAAAD/////AAA6fQAAO5UA/////gIAAAAAAAAAO5UCAAAAAAEAADuoAQAAAAD/////AAA64AAAO/wA/////gIAAAAAAAAAO/0BAAAAAP////8AADtEAAA8XwD////+AgAAAAAAAAA8YAEAAAAA/////wAAO6gAADzRAP////4CAAAAAAAAADzT");

        int randomPort = 0;
        try (final var server = new ServerSocket(0)) {
            randomPort = server.getLocalPort();
        }

        final var reportDir = work.resolve("report");
        final var args = new String[] {
            "start-report-server",
            "--report-directory",
            reportDir.toString(),
            "--working-directory",
            work.toString(),
            "--port",
            Integer.toString(randomPort)
        };
        final var command = new Thread(() -> cli(args), getClass().getName() + ".run");
        command.start();

        final var base = URI.create("http://localhost:" + randomPort);
        try (final var client = HttpClient.newHttpClient()) {
            // give it a chance to start if needed
            final var healthRequest =
                    HttpRequest.newBuilder(base.resolve("/api/health")).build();
            for (var i = 0; i < 600; i++) {
                try {
                    if (client.send(healthRequest, discarding()).statusCode() == 200) {
                        break;
                    }
                } catch (final IOException ioe) {
                    // no-op
                }
                Thread.sleep(100);
            }

            // upload the report (injector step/range)
            assertEquals(
                    200,
                    client.send(
                                    HttpRequest.newBuilder()
                                            .uri(base.resolve("/api/reports"))
                                            .POST(ofByteArray(report))
                                            .build(),
                                    discarding())
                            .statusCode());

            // trigger report generation (range of injector(s) + 1)
            assertEquals(
                    200,
                    client.send(
                                    HttpRequest.newBuilder()
                                            .uri(base.resolve("/api/end"))
                                            .HEAD()
                                            .build(),
                                    discarding())
                            .statusCode());
        } catch (final RuntimeException | Error e) {
            command.interrupt();
            throw e;
        } finally {
            try {
                if (command.isAlive() && !command.join(Duration.ofMinutes(1))) {
                    command.interrupt();
                }
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        // just until we impl an actual report
        assertEquals(
                """
                        = Report

                        - `Pizza Order`
                        """,
                Files.readString(reportDir.resolve("report.adoc")));
    }
}
