package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;

@JsonModel
public record DnsConfig(List<String> nameservers, List<Option> options, List<String> searches) {
    public static boolean isEmpty(final DnsConfig dnsConfig) {
        return dnsConfig == null
                || ((dnsConfig.nameservers() == null || dnsConfig.nameservers().isEmpty())
                        && (dnsConfig.options() == null || dnsConfig.options().isEmpty())
                        && (dnsConfig.searches() == null || dnsConfig.searches().isEmpty()));
    }

    @JsonModel
    public record Option(String name, String value) {}
}
