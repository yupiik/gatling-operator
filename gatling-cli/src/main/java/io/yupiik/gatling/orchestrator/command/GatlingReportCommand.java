package io.yupiik.gatling.orchestrator.command;

import static java.time.Clock.systemUTC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.SEVERE;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClient;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClientConfiguration;
import io.yupiik.fusion.httpclient.core.listener.impl.ExchangeLogger;
import io.yupiik.fusion.json.JsonMapper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@DefaultScoped
@Command(
        name = "gatling-report",
        description =
                "From within the pod which executed the benchmark, send the report to `start-report-server` pod which was started before.")
public class GatlingReportCommand implements Runnable {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final GatlingReportCommandConfiguration configuration;
    private final ScheduledExecutorService threads;
    private final JsonMapper mapper;

    public GatlingReportCommand(
            final GatlingReportCommandConfiguration configuration,
            final ScheduledExecutorService threads,
            final JsonMapper mapper) {
        this.configuration = configuration;
        this.threads = threads;
        this.mapper = mapper;
    }

    @Override
    public void run() {
        // we'll look for $report/$runid/simulation.log files
        final var uri = URI.create(configuration.reporterEndpoint());
        try (final var dir = Files.newDirectoryStream(Path.of(configuration.reportDirectory()), Files::isDirectory);
                final var http = new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                        .setDelegate(HttpClient.newBuilder().executor(threads).build())
                        .setRequestListeners(List.of(new ExchangeLogger(logger, systemUTC(), false))))) {
            final var promises = StreamSupport.stream(dir.spliterator(), false)
                    .flatMap(run -> {
                        final var simulation = run.resolve("simulation.log");
                        return Files.exists(simulation) ? Stream.of(simulation) : Stream.empty();
                    })
                    .map(report -> doSend(http, uri, report))
                    .toList();

            // try our best to send all report if multiples (so do not use allOf())
            IllegalStateException error = null;
            for (final var promise : promises) {
                try {
                    promise.get(configuration.timeout(), MILLISECONDS);
                } catch (final RuntimeException re) {
                    if (error == null) {
                        error = new IllegalStateException("Some report(s) were not sent");
                    }
                    error.addSuppressed(re);
                }
            }
            if (error != null) {
                throw error;
            }
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, re::getMessage);
            throw re;
        } catch (final ExecutionException re) {
            logger.log(SEVERE, re.getCause(), re::getMessage);
            throw new IllegalStateException(re);
        } catch (final TimeoutException | IOException te) {
            logger.log(SEVERE, te, te::getMessage);
            throw new IllegalStateException(te);
        } catch (final InterruptedException ie) {
            logger.log(SEVERE, ie, ie::getMessage);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }

    private CompletableFuture<Void> doSend(final HttpClient client, final URI uri, final Path data) {
        return client.sendAsync(
                        HttpRequest.newBuilder(uri)
                                .POST(file(data))
                                .header("content-type", "application/octect-stream")
                                .header("accept", "*/*")
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(
                        res -> {
                            if (res.statusCode() > 299) {
                                logger.warning(() -> "Can't send report: HTTP " + res.statusCode() + "\n" + res.body());
                                throw new IllegalStateException("HTTP " + res.statusCode());
                            }
                        },
                        client.executor().orElseThrow());
    }

    private HttpRequest.BodyPublisher file(final Path data) {
        try {
            return HttpRequest.BodyPublishers.ofFile(data);
        } catch (final FileNotFoundException fne) {
            throw new IllegalStateException(fne);
        }
    }
}
