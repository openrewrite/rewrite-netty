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

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-maven")
    runtimeOnly("ch.qos.logback:logback-classic:1.2.+")
    runtimeOnly("io.netty:netty-buffer:4.1.100.Final")
    runtimeOnly("io.netty.incubator:netty-incubator-transport-classes-io_uring:0.0.26.Final")
    runtimeOnly("io.netty:netty-transport-classes-io_uring:4.2.2.Final")
}

recipeDependencies {
    // parserClasspath("io.netty:netty-buffer:4.1.+")
}
