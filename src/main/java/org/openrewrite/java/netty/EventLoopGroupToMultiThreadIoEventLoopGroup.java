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

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.openrewrite.java.template.RecipeDescriptor;

@RecipeDescriptor(
        name = "Replace all `EventLoopGroup`s with `MultiThreadIoEventLoopGroup`",
        description = "Replaces Netty's `new *EventLoopGroup` with `new MultiThreadIoEventLoopGroup(*IoHandler.newFactory())`.",
        tags = {"netty"}
)
public class EventLoopGroupToMultiThreadIoEventLoopGroup {
    @RecipeDescriptor(
            name = "Replace `EpollEventLoopGroup` with `MultiThreadIoEventLoopGroup`",
            description = "Replace `new EpollEventLoopGroup()` with `new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory())`.",
            tags = {"netty", "epoll"}
    )
    public static class EpollEventLoopGroupFactory {
        @BeforeTemplate
        EventLoopGroup before() {
            return new EpollEventLoopGroup();
        }

        @AfterTemplate
        EventLoopGroup after() {
            return new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory());
        }
    }

    @RecipeDescriptor(
            name = "Replace `LocalEventLoopGroup` with `MultiThreadIoEventLoopGroup`",
            description = "Replace `new LocalEventLoopGroup()` with `new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory())`.",
            tags = {"netty", "local"}
    )
    public static class LocalEventLoopGroupFactory {
        @BeforeTemplate
        EventLoopGroup before() {
            return new LocalEventLoopGroup();
        }

        @AfterTemplate
        EventLoopGroup after() {
            return new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
        }
    }

    @RecipeDescriptor(
            name = "Replace `NioEventLoopGroup` with `MultiThreadIoEventLoopGroup`",
            description = "Replace `new NioEventLoopGroup()` with `new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())`.",
            tags = {"netty", "nio"}
    )
    public static class NioEventLoopGroupFactory {
        @BeforeTemplate
        EventLoopGroup before() {
            return new NioEventLoopGroup();
        }

        @AfterTemplate
        EventLoopGroup after() {
            return new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        }
    }
}
