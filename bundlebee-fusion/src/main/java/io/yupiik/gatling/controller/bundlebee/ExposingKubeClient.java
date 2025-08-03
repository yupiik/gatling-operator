package io.yupiik.gatling.controller.bundlebee;

import io.yupiik.bundlebee.core.kube.KubeClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Specializes;
import javax.json.JsonObject;

@Specializes
@ApplicationScoped
public class ExposingKubeClient extends KubeClient {
    @Override
    public CompletionStage<HttpResponse<String>> doApply(
            final JsonObject rawDesc,
            final JsonObject preparedDesc,
            final String kindLowerCased,
            final int retry,
            final boolean skipGet) {
        return super.doApply(rawDesc, preparedDesc, kindLowerCased, retry, skipGet);
    }
}
