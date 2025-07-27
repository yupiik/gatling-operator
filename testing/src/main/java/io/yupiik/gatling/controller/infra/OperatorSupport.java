package io.yupiik.gatling.controller.infra;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.yupiik.fusion.testing.FusionSupport;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KubernetesServerExtension.class)
@FusionSupport(modules = JUnitModule.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface OperatorSupport {}
