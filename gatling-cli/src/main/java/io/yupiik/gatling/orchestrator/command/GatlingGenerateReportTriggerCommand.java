package io.yupiik.gatling.orchestrator.command;

import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.time.Clock.systemUTC;
import static java.util.logging.Level.SEVERE;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClient;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClientConfiguration;
import io.yupiik.fusion.httpclient.core.listener.impl.ExchangeLogger;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

@DefaultScoped
@Command(
        name = "do-generate-report",
        description = "Notifies the server behind `start-report-server` command to actually generate the report.")
public class GatlingGenerateReportTriggerCommand implements Runnable {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final GatlingGenerateReportTriggerCommandConfiguration configuration;

    public GatlingGenerateReportTriggerCommand(final GatlingGenerateReportTriggerCommandConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        try (final var http = new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                .setDelegate(HttpClient.newHttpClient())
                .setRequestListeners(List.of(new ExchangeLogger(logger, systemUTC(), false))))) {
            final var res = http.send(
                    HttpRequest.newBuilder()
                            .HEAD()
                            .uri(URI.create(configuration.endpoint()))
                            .timeout(Duration.ofMillis(configuration.timeout()))
                            .build(),
                    discarding());
            if (res.statusCode() > 299) {
                throw new IllegalStateException("Invalid response: HTTP " + res.statusCode());
            }
        } catch (final RuntimeException re) {
            logger.log(SEVERE, re, re::getMessage);
            throw re;
        } catch (final IOException te) {
            logger.log(SEVERE, te, te::getMessage);
            throw new IllegalStateException(te);
        } catch (final InterruptedException ie) {
            logger.log(SEVERE, ie, ie::getMessage);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }
}
