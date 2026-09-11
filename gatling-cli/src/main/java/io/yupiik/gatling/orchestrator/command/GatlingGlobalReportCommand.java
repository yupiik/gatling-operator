package io.yupiik.gatling.orchestrator.command;

import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.joining;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.gatling.charts.stats.LogFileReader;
import io.gatling.core.config.GatlingConfiguration;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import scala.jdk.javaapi.CollectionConverters;

@DefaultScoped
@Command(
        name = "start-report-server",
        description = "Spawns a HTTP server other steps in the pipeline can use to send their report to.")
public class GatlingGlobalReportCommand implements Runnable {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final AtomicInteger id = new AtomicInteger();
    private final Collection<Path> reports = new CopyOnWriteArrayList<>();

    private final GatlingGlobalReportCommandConfiguration configuration;
    private final Path base;

    public GatlingGlobalReportCommand(final GatlingGlobalReportCommandConfiguration configuration) {
        this.configuration = configuration;
        this.base = Path.of(configuration.workDirectory());
    }

    private void generateReport() {
        logger.info(() -> "Generating report using " + reports);
        final var options = GatlingConfiguration.load(); // overridable with system properties
        final var data = reports.stream()
                .map(it -> LogFileReader.apply(
                                it.getParent().getFileName().toString(),
                                it.getParent().getParent(),
                                options)
                        .read())
                .toList();
        logger.info(() -> "Reports: " + data);

        final var report = Path.of(configuration.reportDirectory()).resolve("report.adoc");
        try {
            if (!Files.isDirectory(report.getParent())) {
                Files.createDirectories(report.getParent());
            }

            // todo: implement a reporter
            Files.writeString(
                    report,
                    "= Report\n\n"
                            + data.stream()
                                    .flatMap(it -> CollectionConverters.asJava(it.scenarioNames()).stream())
                                    .sorted()
                                    .map(it -> "- `" + it + "`")
                                    .collect(joining("\n", "", "\n")));
        } catch (final IOException ioe) {
            logger.log(SEVERE, ioe, ioe::getMessage);
            throw new IllegalStateException(ioe);
        }
    }

    private void saveReport(final HttpExchange ex) throws IOException {
        // comply to gatling reader api
        final var file = Files.createDirectories(base.resolve(Integer.toString(id.getAndIncrement())))
                .resolve("simulation.log");
        logger.info(() -> "Creating '" + file + "'");
        try (final var in = ex.getRequestBody();
                final var out = Files.newOutputStream(file)) {
            in.transferTo(out);
        }
        reports.add(file);
        logger.info(() -> "Wrote '" + file + "'");
    }

    @Override
    public void run() {
        if (!Files.exists(base)) {
            try {
                Files.createDirectories(base);
            } catch (final IOException ioe) {
                logger.log(SEVERE, ioe, () -> "Can't create directory '" + base + "'");
                throw new IllegalStateException(ioe);
            }
        }

        final var latch = new CountDownLatch(1);
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(configuration.address(), configuration.port()), 128);
            server.createContext("/").setHandler(ex -> {
                if (onExchange(ex)) {
                    latch.countDown();
                }
            });
            server.start();
            logger.info("Server started on port " + server.getAddress().getPort());
            latch.await();
        } catch (final IOException ioe) {
            logger.log(SEVERE, ioe, () -> "Can't start server");
            throw new IllegalStateException(ioe);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private boolean onExchange(final HttpExchange ex) throws IOException {
        logger.info(() -> ex.getRequestMethod() + " " + ex.getRequestURI());
        var exit = false;
        try (ex) {
            if ("POST".equals(ex.getRequestMethod())
                    && "/api/reports".equals(ex.getRequestURI().getPath())) {
                saveReport(ex);
                writeEmptyResponse(ex);
            } else if ("HEAD".equals(ex.getRequestMethod())
                    && "/api/end".equals(ex.getRequestURI().getPath())) {
                try {
                    generateReport();
                    ex.sendResponseHeaders(200, -1);
                } finally {
                    exit = true;
                    logger.info(() -> "Exiting");
                }
            } else if (("HEAD".equals(ex.getRequestMethod()) || "GET".equals(ex.getRequestMethod()))
                    && "/api/health".equals(ex.getRequestURI().getPath())) {
                writeEmptyResponse(ex);
            } else {
                ex.sendResponseHeaders(404, 0);
            }
        } catch (final Exception e) {
            logger.log(SEVERE, e, e::getMessage);
            ex.sendResponseHeaders(500, 0);
        }
        return exit;
    }

    private static void writeEmptyResponse(final HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("content-type", "application/json");
        ex.sendResponseHeaders(200, 2);
        ex.getResponseBody().write('{');
        ex.getResponseBody().write('}');
    }
}
