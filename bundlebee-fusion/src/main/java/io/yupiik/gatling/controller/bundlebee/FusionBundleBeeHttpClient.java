package io.yupiik.gatling.controller.bundlebee;

import io.yupiik.bundlebee.core.kube.HttpKubeClient;
import io.yupiik.bundlebee.core.kube.KubeConfig;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

// hack to switch default bundlebee client to the fusion/operator one (single config)
@ApplicationScoped
public class FusionBundleBeeHttpClient implements HttpKubeClient {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final KubernetesClient client;
    private final String base;
    private final Duration timeout;

    public FusionBundleBeeHttpClient(final KubernetesClient client) {
        this.client = client;
        this.base = client == null ? null : client.base().toASCIIString();
        this.timeout = Duration.ofMinutes(1);
    }

    @Override
    public boolean isDryRun() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return logger.isLoggable(Level.FINEST);
    }

    @Override
    public String getBaseApi() {
        return base;
    }

    @Override
    public String getNamespace() {
        return client.namespace().orElse("default");
    }

    @Override // only for kubeconfig.cluster.* placeholders, not used in the operator
    public KubeConfig getLoadedKubeConfig() {
        return new KubeConfig();
    }

    @Override
    public HttpClient getClient() {
        return client;
    }

    @Override
    public CompletionStage<HttpResponse<String>> execute(final HttpRequest.Builder builder, final String urlOrPath) {
        return client.sendAsync(
                prepareRequest(builder, urlOrPath), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Override
    public HttpRequest prepareRequest(final HttpRequest.Builder builder, final String urlOrPath) {
        builder.uri(URI.create(
                urlOrPath.startsWith("http:") || urlOrPath.startsWith("https:") ? urlOrPath : (base + urlOrPath)));
        builder.timeout(timeout);
        return builder.build();
    }
}
