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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class RemoveChannelStateEventParameter extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove ChannelStateEvent parameter from handler methods";
    }

    @Override
    public String getDescription() {
        return "Removes `ChannelStateEvent` parameters from Netty channel handler method declarations, " +
               "as Netty 4 handler methods no longer take this parameter.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);

                List<Statement> newParams = ListUtils.map(m.getParameters(), param -> {
                    if (param instanceof J.VariableDeclarations &&
                        TypeUtils.isOfClassType(((J.VariableDeclarations) param).getType(), "org.jboss.netty.channel.ChannelStateEvent")) {
                        maybeRemoveImport("org.jboss.netty.channel.ChannelStateEvent");
                        return null;
                    }
                    return param;
                });

                if (newParams == m.getParameters()) {
                    return m;
                }

                return m.withParameters(newParams);
            }
        };
    }
}
