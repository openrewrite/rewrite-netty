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
package org.openrewrite.java.netty.upgrade._3_2_to_4_1_;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class StringEncoderToStandardCharsetsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new StringEncoderToStandardCharsets())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "netty"));
    }

    @DocumentExample
    @Test
    void changeStringEncoderConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jboss.netty.handler.codec.string.StringEncoder;

              class Test {
                  public void test() {
                      new StringEncoder("UTF-8");
                  }
              }
              """,
            """
              import io.netty.handler.codec.string.StringEncoder;

              import java.nio.charset.StandardCharsets;

              class Test {
                  public void test() {
                      new StringEncoder(StandardCharsets.UTF_8);
                  }
              }
              """
          )
        );
    }
}
