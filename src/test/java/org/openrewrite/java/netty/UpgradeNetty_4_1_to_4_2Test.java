/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.netty;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeNetty_4_1_to_4_2Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResource(
            "/META-INF/rewrite/netty-4_1_to_4_2.yml",
            "org.openrewrite.netty.UpgradeNetty_4_1_to_4_2")
          .parser(JavaParser.fromJavaVersion().classpath(
            "netty-buffer",
            "netty-incubator-transport-classes-io_uring",
            "netty-transport-classes-io_uring"));
    }

    @DocumentExample
    @Test
    void nettyUpgradeToVersion4_2() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.netty</groupId>
                          <artifactId>netty-buffer</artifactId>
                          <version>4.1.100.Final</version>
                      </dependency>
                      <dependency>
                          <groupId>io.netty.incubator</groupId>
                          <artifactId>netty-incubator-transport-classes-io_uring</artifactId>
                          <version>0.0.26.Final</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .describedAs("Expected library version 4.2.x")
              .containsPattern("4\\.2\\.\\d+\\.Final")
              .doesNotContainPattern("4\\.1\\.\\d+\\.Final")
              .doesNotContain("incubator")
              .contains("netty-transport-classes-io_uring")
              .contains("netty-buffer")
              .actual())),
          //language=java
          java(
            """
              import io.netty.buffer.ByteBuf;
              import io.netty.incubator.channel.uring.IOUring;

              class Test {
                  static void helloNetty() {
                      Object[] input = new Object[] { "one", "two" };
                  }
              }
              """,
            """
              import io.netty.buffer.ByteBuf;

              class Test {
                  static void helloNetty() {
                      Object[] input = new Object[] { "one", "two" };
                  }
              }
              """
          )
        );
    }
}
