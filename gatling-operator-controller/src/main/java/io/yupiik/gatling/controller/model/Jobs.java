package io.yupiik.gatling.controller.model;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import java.util.List;

@JsonModel
public record Jobs(List<Job> items) {}
