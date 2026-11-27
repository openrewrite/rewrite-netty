/*
 * Copyright 2026 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "netty-common-4",
            "netty-transport-4",
            "netty-transport-classes-epoll",
            "netty-transport-classes-kqueue"));
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

    @CsvSource(delimiter = '|', value = {
      "epoll|Epoll|4",
      "epoll|Epoll|threadFactory",
      "epoll|Epoll|4, threadFactory",
      "epoll|Epoll|4, executor",
      "kqueue|KQueue|",
      "kqueue|KQueue|4",
      "kqueue|KQueue|threadFactory",
      "kqueue|KQueue|4, threadFactory",
      "kqueue|KQueue|4, executor",
      "local|Local|4",
      "local|Local|threadFactory",
      "local|Local|4, threadFactory",
      "nio|Nio|4",
      "nio|Nio|threadFactory",
      "nio|Nio|4, threadFactory",
      "nio|Nio|4, executor",
    })
    @ParameterizedTest
    void replaceWithArguments(String pkg, String transport, @Nullable String args) {
        String group = transport + "EventLoopGroup";
        String handler = transport + "IoHandler";
        String before = args == null ? "" : args;
        String after = before.isEmpty() ? "" : before + ", ";
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.%s.%s;
              import java.util.concurrent.Executor;
              import java.util.concurrent.ThreadFactory;

              class Test {
                  EventLoopGroup create(ThreadFactory threadFactory, Executor executor) {
                      return new %s(%s);
                  }
              }
              """.formatted(pkg, group, group, before),
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.%s.%s;

              import java.util.concurrent.Executor;
              import java.util.concurrent.ThreadFactory;

              class Test {
                  EventLoopGroup create(ThreadFactory threadFactory, Executor executor) {
                      return new MultiThreadIoEventLoopGroup(%s%s.newFactory());
                  }
              }
              """.formatted(pkg, handler, after, handler)
          )
        );
    }

    @Test
    void replaceInAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  EventLoopGroup group;

                  void init() {
                      group = new NioEventLoopGroup(4);
                  }
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  EventLoopGroup group;

                  void init() {
                      group = new MultiThreadIoEventLoopGroup(4, NioIoHandler.newFactory());
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceInReturn() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  EventLoopGroup create() {
                      return new NioEventLoopGroup();
                  }
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  EventLoopGroup create() {
                      return new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceAsMethodArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.bootstrap.Bootstrap;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  Bootstrap create() {
                      return new Bootstrap().group(new NioEventLoopGroup(2));
                  }
              }
              """,
            """
              import io.netty.bootstrap.Bootstrap;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  Bootstrap create() {
                      return new Bootstrap().group(new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory()));
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceAsConstructorArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  static class Holder {
                      Holder(EventLoopGroup group) {
                      }
                  }

                  Holder create() {
                      return new Holder(new NioEventLoopGroup());
                  }
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  static class Holder {
                      Holder(EventLoopGroup group) {
                      }
                  }

                  Holder create() {
                      return new Holder(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceBothBranchesOfTernary() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.epoll.Epoll;
              import io.netty.channel.epoll.EpollEventLoopGroup;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  EventLoopGroup group = Epoll.isAvailable() ? new EpollEventLoopGroup(2) : (new NioEventLoopGroup(2));
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.epoll.Epoll;
              import io.netty.channel.epoll.EpollIoHandler;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  EventLoopGroup group = Epoll.isAvailable() ? new MultiThreadIoEventLoopGroup(2, EpollIoHandler.newFactory()) : (new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory()));
              }
              """
          )
        );
    }

    @Test
    void replaceLocalVariableWithNonLiteralThreadCount() {
        rewriteRun(
          //language=java
          java(
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.nio.NioEventLoopGroup;

              class Test {
                  void run() {
                      EventLoopGroup group = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
                  }
              }
              """,
            """
              import io.netty.channel.EventLoopGroup;
              import io.netty.channel.MultiThreadIoEventLoopGroup;
              import io.netty.channel.nio.NioIoHandler;

              class Test {
                  void run() {
                      EventLoopGroup group = new MultiThreadIoEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2, NioIoHandler.newFactory());
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadyMigrated() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.EventLoopGroup;
                  import io.netty.channel.MultiThreadIoEventLoopGroup;
                  import io.netty.channel.nio.NioIoHandler;

                  class Test {
                      EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
                  }
                  """
              )
            );
        }

        @Test
        void assignedToSubclassType() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      NioEventLoopGroup group = new NioEventLoopGroup(4);
                  }
                  """
              )
            );
        }

        @Test
        void returnedAsSubclassType() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      NioEventLoopGroup create() {
                          return new NioEventLoopGroup(4);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assignedToSubclassTypedVariable() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      NioEventLoopGroup group;

                      void init() {
                          group = new NioEventLoopGroup(4);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void passedAsSubclassTypedArgument() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      void take(NioEventLoopGroup group) {
                      }

                      void run() {
                          take(new NioEventLoopGroup(4));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void varDeclaration() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      void run() {
                          var group = new NioEventLoopGroup(4);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void returnedFromLambda() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.EventLoopGroup;
                  import io.netty.channel.nio.NioEventLoopGroup;

                  import java.util.function.Supplier;

                  class Test {
                      Supplier<EventLoopGroup> supplier = () -> {
                          return new NioEventLoopGroup();
                      };
                  }
                  """
              )
            );
        }

        @Test
        void methodInvokedOnNewInstance() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      void run() {
                          new NioEventLoopGroup(4).setIoRatio(50);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void unsupportedOverload() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.EventLoopGroup;
                  import io.netty.channel.nio.NioEventLoopGroup;

                  import java.nio.channels.spi.SelectorProvider;
                  import java.util.concurrent.Executor;

                  class Test {
                      EventLoopGroup create(Executor executor) {
                          return new NioEventLoopGroup(1, executor, SelectorProvider.provider());
                      }
                  }
                  """
              )
            );
        }

        @Test
        void anonymousSubclass() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.EventLoopGroup;
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      EventLoopGroup group = new NioEventLoopGroup(1) {
                      };
                  }
                  """
              )
            );
        }

        @Test
        void ternaryAssignedToSubclassType() {
            rewriteRun(
              //language=java
              java(
                """
                  import io.netty.channel.nio.NioEventLoopGroup;

                  class Test {
                      NioEventLoopGroup group(boolean small) {
                          return small ? new NioEventLoopGroup(1) : new NioEventLoopGroup();
                      }
                  }
                  """
              )
            );
        }
    }
}
