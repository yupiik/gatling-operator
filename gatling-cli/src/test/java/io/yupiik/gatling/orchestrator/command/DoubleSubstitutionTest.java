package io.yupiik.gatling.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yupiik.bundlebee.core.descriptor.Manifest;
import io.yupiik.bundlebee.core.lang.Substitutor;
import io.yupiik.bundlebee.core.service.AlveolusHandler;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class DoubleSubstitutionTest {
    @Test
    void run() {
        final var map = new HashMap<String, String>();
        map.put("gatling-operator.implicit.parent-name", "dad");

        final var sub = new Substitutor(map::get);
        final var alveolus = new Manifest.Alveolus();
        final var desc = new AlveolusHandler.LoadedDescriptor(new Manifest.Descriptor(), "", "", "", "");

        final var content =
                """
                generic-job.command: |
                  [
                      "sh", "-c", "
                        echo \\"step: {{gatling-operator.implicit.index}}:{{gatling-operator.implicit.range}}\\" &&
                        curl -v http://\\{{gatling-operator.implicit.parent-name}}-0-0 &&
                        sleep 10
                      "
                  ]
                """;

        // operator interpolation (deploy)
        var res = sub.replace(alveolus, desc, content, "1");

        // bench/orchestrator command interpolation (deploy)
        map.put("gatling-operator.implicit.index", "0");
        map.put("gatling-operator.implicit.range", "0");
        res = sub.replace(alveolus, desc, res, "1");

        assertEquals(
                """
                generic-job.command: |
                  [
                      "sh", "-c", "
                        echo \\"step: null:null\\" &&
                        curl -v http://dad-0-0 &&
                        sleep 10
                      "
                  ]
                """,
                res);
    }
}
