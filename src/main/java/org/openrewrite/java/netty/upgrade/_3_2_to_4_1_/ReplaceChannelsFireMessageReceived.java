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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

public class ReplaceChannelsFireMessageReceived extends Recipe {

    private static final MethodMatcher FIRE_MESSAGE_RECEIVED =
            new MethodMatcher("org.jboss.netty.channel.Channels fireMessageReceived(..)", true);

    @Override
    public String getDisplayName() {
        return "Replace Channels.fireMessageReceived(..) with ctx.fireChannelRead(e)";
    }

    @Override
    public String getDescription() {
        return "Replaces Netty 3 Channels.fireMessageReceived(channel, message) with Netty 4 ctx.fireChannelRead(message).";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            final JavaTemplate replacement = JavaTemplate.builder("#{any()}.fireChannelRead(#{any()})")
                    .build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);

                if (!FIRE_MESSAGE_RECEIVED.matches(m)) {
                    return m;
                }

                List<Expression> args = m.getArguments();
                if (args.size() < 2) {
                    return m;
                }

                Expression channel = args.get(0);
                Expression message = args.get(1);

                return replacement.apply(
                        updateCursor(m),
                        m.getCoordinates().replace(),
                        channel,
                        message
                );
            }
        };
    }
}