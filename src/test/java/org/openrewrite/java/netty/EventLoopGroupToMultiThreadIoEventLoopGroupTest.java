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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EventLoopGroupToMultiThreadIoEventLoopGroupTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EventLoopGroupToMultiThreadIoEventLoopGroupRecipes())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "netty-transport", "netty-transport-classes-epoll"));
    }

    @DocumentExample
    @Test
    void replaceEpoll() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.epoll.EpollEventLoopGroup;

              class Test {
                  EventLoopGroup group1 = new EpollEventLoopGroup();
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.epoll.EpollIoHandler;

              class Test {
                  EventLoopGroup group1 = new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory());
              }
              """
          )
        );
    }

    @Test
    void replaceLocal() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.local.LocalEventLoopGroup;

              class Test {
                  EventLoopGroup group2 = new LocalEventLoopGroup();
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.local.LocalIoHandler;

              class Test {
                  EventLoopGroup group2 = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
              }
              """
          )
        );
    }

    @Test
    void replaceNio() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  EventLoopGroup group3 = new NioEventLoopGroup();
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  EventLoopGroup group3 = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
              }
              """
          )
        );
    }
}
