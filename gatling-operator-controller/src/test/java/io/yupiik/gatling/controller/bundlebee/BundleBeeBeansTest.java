package io.yupiik.gatling.controller.bundlebee;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yupiik.fusion.testing.Fusion;
import io.yupiik.gatling.controller.infra.OperatorSupport;
import io.yupiik.gatling.controller.service.BundleBeeService;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

@OperatorSupport
class BundleBeeBeansTest {
    @Test
    void visit(@Fusion final BundleBeeService handler) throws ExecutionException, InterruptedException {
        assertNotNull(handler.orchestratorAlveolus().toCompletableFuture().get());
    }
}
