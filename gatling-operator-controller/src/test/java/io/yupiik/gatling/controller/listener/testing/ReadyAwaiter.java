package io.yupiik.gatling.controller.listener.testing;

import io.yupiik.bundlebee.core.kube.ApiPreloader;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.framework.build.api.order.Order;
import io.yupiik.gatling.controller.infra.Kubernetes;
import javax.enterprise.inject.se.SeContainer;

// ensure the automatic startup requests are done and we can start running tests
@DefaultScoped
public class ReadyAwaiter {
    protected void onStart(
            @OnEvent @Order(Integer.MAX_VALUE) final Start start,
            final Kubernetes kubernetes,
            final SeContainer container) {
        kubernetes.requests(2).clear(); // await watch is done

        // ensure prefetch of resources is done - and avoid to interfere with the test
        container.select(ApiPreloader.class).get().getBaseUrls().put("jobs", "/api/v1/namespaces/${namespace}/jobs");
        kubernetes.requests(1).clear(); // await watch is done
    }
}
