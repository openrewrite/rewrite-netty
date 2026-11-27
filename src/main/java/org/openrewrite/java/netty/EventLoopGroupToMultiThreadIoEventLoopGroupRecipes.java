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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Collections.singleton;

public class EventLoopGroupToMultiThreadIoEventLoopGroupRecipes extends Recipe {

    private static final String MULTI_THREAD = "io.netty.channel.MultiThreadIoEventLoopGroup";
    private static final String THREAD_FACTORY = "java.util.concurrent.ThreadFactory";
    private static final String EXECUTOR = "java.util.concurrent.Executor";

    private static final Map<String, String> HANDLERS = new HashMap<>();

    static {
        HANDLERS.put("io.netty.channel.epoll.EpollEventLoopGroup", "io.netty.channel.epoll.EpollIoHandler");
        HANDLERS.put("io.netty.channel.kqueue.KQueueEventLoopGroup", "io.netty.channel.kqueue.KQueueIoHandler");
        HANDLERS.put("io.netty.channel.local.LocalEventLoopGroup", "io.netty.channel.local.LocalIoHandler");
        HANDLERS.put("io.netty.channel.nio.NioEventLoopGroup", "io.netty.channel.nio.NioIoHandler");
    }

    private static final String GROUP_CONSTRUCTOR = "io.netty.channel..*EventLoopGroup <constructor>";
    private static final List<MethodMatcher> SUPPORTED_CONSTRUCTORS = Arrays.asList(
            new MethodMatcher(GROUP_CONSTRUCTOR + "()"),
            new MethodMatcher(GROUP_CONSTRUCTOR + "(int)"),
            new MethodMatcher(GROUP_CONSTRUCTOR + "(" + THREAD_FACTORY + ")"),
            new MethodMatcher(GROUP_CONSTRUCTOR + "(int, " + THREAD_FACTORY + ")"),
            new MethodMatcher(GROUP_CONSTRUCTOR + "(int, " + EXECUTOR + ")")
    );

    // `MultiThreadIoEventLoopGroup` and its supertypes; the transport-specific groups are subtypes of it, so
    // a group can only be replaced where one of these is expected.
    private static final Set<String> COMPATIBLE_TARGETS = new HashSet<>(Arrays.asList(
            "java.lang.Object",
            "java.lang.Iterable",
            EXECUTOR,
            "java.util.concurrent.ExecutorService",
            "java.util.concurrent.ScheduledExecutorService",
            "io.netty.channel.EventLoopGroup",
            "io.netty.util.concurrent.EventExecutorGroup",
            "io.netty.util.concurrent.AbstractEventExecutorGroup",
            "io.netty.util.concurrent.MultithreadEventExecutorGroup",
            "io.netty.channel.MultithreadEventLoopGroup",
            MULTI_THREAD
    ));

    @Getter
    final String displayName = "Replace all `EventLoopGroup`s with `MultiThreadIoEventLoopGroup`";

    @Getter
    final String description = "Replaces Netty's `new *EventLoopGroup(..)` with `new MultiThreadIoEventLoopGroup(.., *IoHandler.newFactory())`. " +
            "Only applied where an `EventLoopGroup` (or a supertype) is expected, as `MultiThreadIoEventLoopGroup` " +
            "is not assignable to the transport-specific group types.";

    @Getter
    final Set<String> tags = singleton("netty");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GROUP_CONSTRUCTOR + "(..)"), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                JavaType.Method constructor = nc.getConstructorType();
                if (nc.getBody() != null || constructor == null) {
                    return nc;
                }
                String group = constructor.getDeclaringType().getFullyQualifiedName();
                String handler = HANDLERS.get(group);
                if (handler == null ||
                    SUPPORTED_CONSTRUCTORS.stream().noneMatch(m -> m.matches(constructor)) ||
                    !targetAcceptsEventLoopGroup(getCursor())) {
                    return nc;
                }

                StringBuilder template = new StringBuilder("new ").append(MULTI_THREAD).append('(');
                for (JavaType paramType : constructor.getParameterTypes()) {
                    template.append("#{any(").append(TypeUtils.toString(paramType)).append(")}, ");
                }
                template.append(handler).append(".newFactory())");

                maybeRemoveImport(group);
                maybeAddImport(MULTI_THREAD);
                maybeAddImport(handler);
                return JavaTemplate.builder(template.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                "netty-common-4", "netty-transport-4",
                                "netty-transport-classes-epoll-4", "netty-transport-classes-kqueue-4"))
                        .build()
                        .apply(getCursor(), nc.getCoordinates().replace(),
                                ListUtils.filter(nc.getArguments(), arg -> !(arg instanceof J.Empty)).toArray());
            }
        });
    }

    private static boolean targetAcceptsEventLoopGroup(Cursor cursor) {
        J self = cursor.getValue();
        Cursor parentCursor = cursor.getParentTreeCursor();
        while (parentCursor.getValue() instanceof J.Parentheses || parentCursor.getValue() instanceof J.Ternary) {
            self = parentCursor.getValue();
            parentCursor = parentCursor.getParentTreeCursor();
        }
        Object parent = parentCursor.getValue();
        JavaType expected = null;
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            expected = ((J.VariableDeclarations.NamedVariable) parent).getType();
        } else if (parent instanceof J.Assignment) {
            expected = ((J.Assignment) parent).getVariable().getType();
        } else if (parent instanceof J.Return) {
            Object enclosing = parentCursor.dropParentUntil(p -> p instanceof J.Lambda || p instanceof J.MethodDeclaration).getValue();
            if (enclosing instanceof J.MethodDeclaration) {
                JavaType.Method methodType = ((J.MethodDeclaration) enclosing).getMethodType();
                expected = methodType == null ? null : methodType.getReturnType();
            }
        } else if (parent instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) parent;
            expected = parameterType(mi.getMethodType(), mi.getArguments(), self);
        } else if (parent instanceof J.NewClass) {
            J.NewClass nc = (J.NewClass) parent;
            expected = parameterType(nc.getConstructorType(), nc.getArguments(), self);
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expected);
        return fq != null && COMPATIBLE_TARGETS.contains(fq.getFullyQualifiedName());
    }

    private static @Nullable JavaType parameterType(JavaType.@Nullable Method method, List<Expression> arguments, J self) {
        if (method == null) {
            return null;
        }
        int i = arguments.indexOf(self);
        List<JavaType> parameterTypes = method.getParameterTypes();
        return 0 <= i && i < parameterTypes.size() ? parameterTypes.get(i) : null;
    }
}
