plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Netty Migration"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")

    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }
    compileOnly("io.netty:netty-all:4.2.+")
    runtimeOnly("ch.qos.logback:logback-classic:1.2.+")
    runtimeOnly("io.netty:netty-buffer:4.1.100.Final")
    runtimeOnly("io.netty.incubator:netty-incubator-transport-classes-io_uring:0.0.26.Final")
    runtimeOnly("io.netty:netty-transport-classes-io_uring:4.2.2.Final")

    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-maven")
    testImplementation("org.openrewrite:rewrite-test")

    testRuntimeOnly("io.netty:netty-all:4.2.+")
}

recipeDependencies {
    // parserClasspath("io.netty:netty-buffer:4.1.+")
}
