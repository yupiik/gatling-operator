package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;
import java.util.Map;

@JsonModel
public record Affinity(NodeAffinity nodeAffinity, AffinitySpec podAffinity, AffinitySpec podAntiAffinity) {
    public static boolean isEmpty(final Affinity affinity) {
        return affinity == null
                || (NodeAffinity.isEmpty(affinity.nodeAffinity())
                        && AffinitySpec.isEmpty(affinity.podAffinity())
                        && AffinitySpec.isEmpty(affinity.podAntiAffinity()));
    }

    @JsonModel
    public record NodeAffinity(
            List<PreferredSchedulingTerm> preferredDuringSchedulingIgnoredDuringExecution,
            RequiredDuringSchedulingIgnoredDuringExecution requiredDuringSchedulingIgnoredDuringExecution) {
        public static boolean isEmpty(final NodeAffinity nodeAffinity) {
            return nodeAffinity == null
                    || (nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution() == null
                                    || nodeAffinity
                                            .preferredDuringSchedulingIgnoredDuringExecution()
                                            .isEmpty())
                            && (nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution() == null
                                    || nodeAffinity
                                                    .requiredDuringSchedulingIgnoredDuringExecution()
                                                    .nodeSelectorTerms()
                                            == null
                                    || nodeAffinity
                                            .requiredDuringSchedulingIgnoredDuringExecution()
                                            .nodeSelectorTerms()
                                            .isEmpty());
        }
    }

    @JsonModel
    public enum LabelSelectorOperator {
        In,
        NotIn,
        Exists,
        DoesNotExist
    }

    @JsonModel
    public enum NodeSelectorOperator {
        In,
        NotIn,
        Exists,
        DoesNotExist,
        Gt,
        Lt
    }

    @JsonModel
    public record PreferredSchedulingTerm(int weight, NodeSelectorTerm preference) {}

    @JsonModel
    public record RequiredDuringSchedulingIgnoredDuringExecution(List<NodeSelectorTerm> nodeSelectorTerms) {}

    @JsonModel
    public record NodeSelectorTerm(List<NodeMatchExpression> matchExpressions, List<NodeMatchExpression> matchFields) {}

    @JsonModel
    public record NodeMatchExpression(String key, NodeSelectorOperator operator, List<String> values) {}

    @JsonModel
    public record LabelMatchExpression(String key, LabelSelectorOperator operator, List<String> values) {}

    @JsonModel
    public record AffinitySpec(
            List<WeightedPodAffinityTerm> preferredDuringSchedulingIgnoredDuringExecution,
            List<PodAffinityTerm> requiredDuringSchedulingIgnoredDuringExecution) {
        public static boolean isEmpty(final AffinitySpec affinitySpec) {
            return affinitySpec == null
                    || ((affinitySpec.preferredDuringSchedulingIgnoredDuringExecution() == null
                                    || affinitySpec
                                            .preferredDuringSchedulingIgnoredDuringExecution()
                                            .isEmpty())
                            && (affinitySpec.requiredDuringSchedulingIgnoredDuringExecution() == null
                                    || affinitySpec
                                            .requiredDuringSchedulingIgnoredDuringExecution()
                                            .isEmpty()));
        }
    }

    @JsonModel
    public record WeightedPodAffinityTerm(int weight, PodAffinityTerm podAffinityTerm) {}

    @JsonModel
    public record PodAffinityTerm(
            LabelSelector labelSelector,
            List<String> matchLabelKeys,
            List<String> mismatchLabelKeys,
            LabelSelector namespaceSelector,
            List<String> namespaces,
            String topologyKey) {}

    @JsonModel
    public record LabelSelector(List<LabelMatchExpression> matchExpressions, Map<String, String> matchLabels) {}
}
