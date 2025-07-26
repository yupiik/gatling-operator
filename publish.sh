#! /bin/bash

mvn clean package jib:${JIB_BUILD:-dockerBuild} -pl gatling-operator-controller -pl gatling-cli -am -DskipTests
