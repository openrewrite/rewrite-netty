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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.emptyList;

public class ChangeMessageEventParameterToObject extends Recipe {

    private static final String MESSAGE_EVENT = "org.jboss.netty.channel.MessageEvent";

    private static final MethodMatcher CHANNEL_READ = new MethodMatcher(
            "*..* channelRead(org.jboss.netty.channel.ChannelHandlerContext, " + MESSAGE_EVENT + ")");

    private static final JavaType.FullyQualified OBJECT = JavaType.ShallowClass.build("java.lang.Object");

    @Getter
    final String displayName = "Change `MessageEvent` parameter of `channelRead` to `Object`";

    @Getter
    final String description = "Replaces the `MessageEvent` parameter of `channelRead` handler methods with `Object`, " +
            "as Netty 4 passes the message itself rather than an event.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(MESSAGE_EVENT, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);
                if (!CHANNEL_READ.matches(m.getMethodType())) {
                    return m;
                }

                maybeRemoveImport(MESSAGE_EVENT);
                m = m.withParameters(ListUtils.map(m.getParameters(), param -> {
                    if (param instanceof J.VariableDeclarations &&
                        TypeUtils.isOfClassType(((J.VariableDeclarations) param).getType(), MESSAGE_EVENT)) {
                        return toObject((J.VariableDeclarations) param);
                    }
                    return param;
                }));

                JavaType.Method methodType = m.getMethodType();
                if (methodType != null) {
                    methodType = methodType.withParameterTypes(ListUtils.map(methodType.getParameterTypes(),
                            parameterType -> TypeUtils.isOfClassType(parameterType, MESSAGE_EVENT) ? OBJECT : parameterType));
                    return m.withMethodType(methodType).withName(m.getName().withType(methodType));
                }
                return m;
            }

            private J.VariableDeclarations toObject(J.VariableDeclarations vd) {
                TypeTree typeExpression = vd.getTypeExpression();
                if (typeExpression == null) {
                    return vd;
                }
                TypeTree object = new J.Identifier(Tree.randomId(), typeExpression.getPrefix(), typeExpression.getMarkers(),
                        emptyList(), "Object", OBJECT, null);
                return vd.withTypeExpression(object)
                        .withVariables(ListUtils.map(vd.getVariables(), v -> {
                            JavaType.Variable variableType = v.getVariableType();
                            if (variableType == null) {
                                return v.withName(v.getName().withType(OBJECT));
                            }
                            JavaType.Variable newVariableType = variableType.withType(OBJECT);
                            return v.withVariableType(newVariableType)
                                    .withName(v.getName().withType(OBJECT).withFieldType(newVariableType));
                        }));
            }
        });
    }
}
