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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

/**
 * Replaces {@code new *EventLoopGroup(..)} with {@code new MultiThreadIoEventLoopGroup(.., *IoHandler.newFactory())}.
 * <p>
 * The replacement is only applied when the new instance is used where an {@code EventLoopGroup}-compatible
 * type is expected, since {@code MultiThreadIoEventLoopGroup} is not a subtype of the
 * transport-specific groups (e.g. {@code NioEventLoopGroup}).
 */
public class EventLoopGroupToMultiThreadIoEventLoopGroupRecipes extends Recipe {

    private static final String MULTI_THREAD = "io.netty.channel.MultiThreadIoEventLoopGroup";
    private static final String THREAD_FACTORY = "java.util.concurrent.ThreadFactory";
    private static final String EXECUTOR = "java.util.concurrent.Executor";

    /** Group type to its {@code IoHandler} type. */
    private static final Map<String, String> HANDLERS = new HashMap<>();

    static {
        HANDLERS.put("io.netty.channel.epoll.EpollEventLoopGroup", "io.netty.channel.epoll.EpollIoHandler");
        HANDLERS.put("io.netty.channel.kqueue.KQueueEventLoopGroup", "io.netty.channel.kqueue.KQueueIoHandler");
        HANDLERS.put("io.netty.channel.local.LocalEventLoopGroup", "io.netty.channel.local.LocalIoHandler");
        HANDLERS.put("io.netty.channel.nio.NioEventLoopGroup", "io.netty.channel.nio.NioIoHandler");
    }

    /** Constructor signatures that have a direct {@code MultiThreadIoEventLoopGroup} equivalent. */
    private static final Set<String> SUPPORTED_SIGNATURES = new HashSet<>(Arrays.asList(
            "",
            "int",
            THREAD_FACTORY,
            "int," + THREAD_FACTORY,
            "int," + EXECUTOR
    ));

    /** Expected types that a {@code MultiThreadIoEventLoopGroup} can be assigned to. */
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
    final Set<String> tags = new HashSet<>(Arrays.asList("netty"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("io.netty.channel.EventLoopGroup", true), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J j = super.visitNewClass(newClass, ctx);
                if (!(j instanceof J.NewClass)) {
                    return j;
                }
                J.NewClass elem = (J.NewClass) j;
                if (elem.getBody() != null || elem.getConstructorType() == null) {
                    return j;
                }
                JavaType.FullyQualified declaring = elem.getConstructorType().getDeclaringType();
                String handler = HANDLERS.get(declaring.getFullyQualifiedName());
                if (handler == null) {
                    return j;
                }
                List<JavaType> paramTypes = elem.getConstructorType().getParameterTypes();
                if (!SUPPORTED_SIGNATURES.contains(signature(paramTypes))) {
                    return j;
                }
                if (!targetAcceptsEventLoopGroup(getCursor())) {
                    return j;
                }

                StringBuilder template = new StringBuilder("new ").append(MULTI_THREAD).append('(');
                for (JavaType paramType : paramTypes) {
                    template.append("#{any(").append(typeName(paramType)).append(")}, ");
                }
                template.append(handler).append(".newFactory())");

                List<Expression> args = new ArrayList<>();
                for (Expression arg : elem.getArguments()) {
                    if (!(arg instanceof J.Empty)) {
                        args.add(arg);
                    }
                }

                maybeRemoveImport(declaring.getFullyQualifiedName());
                maybeAddImport(MULTI_THREAD);
                maybeAddImport(handler);
                return JavaTemplate.builder(template.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                "netty-common-4", "netty-transport-4",
                                "netty-transport-classes-epoll-4", "netty-transport-classes-kqueue-4"))
                        .build()
                        .apply(getCursor(), elem.getCoordinates().replace(), args.toArray())
                        .withPrefix(elem.getPrefix());
            }
        });
    }

    private static String signature(List<JavaType> paramTypes) {
        StringBuilder sb = new StringBuilder();
        for (JavaType paramType : paramTypes) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(typeName(paramType));
        }
        return sb.toString();
    }

    private static String typeName(JavaType type) {
        if (type instanceof JavaType.Primitive) {
            return ((JavaType.Primitive) type).getKeyword();
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? String.valueOf(type) : fq.getFullyQualifiedName();
    }

    /**
     * Whether the expression at the cursor is used where a {@code MultiThreadIoEventLoopGroup} is acceptable.
     * Conservative: anything not positively identified is treated as not acceptable.
     */
    private static boolean targetAcceptsEventLoopGroup(Cursor cursor) {
        Cursor parentCursor = cursor.getParentTreeCursor();
        Object parent = parentCursor.getValue();
        J self = cursor.getValue();
        JavaType expected = null;
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            expected = ((J.VariableDeclarations.NamedVariable) parent).getType();
        } else if (parent instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) parent;
            if (assignment.getAssignment() != self) {
                return false;
            }
            expected = assignment.getVariable().getType();
        } else if (parent instanceof J.Return) {
            for (Iterator<Object> it = parentCursor.getPath(); it.hasNext(); ) {
                Object ancestor = it.next();
                if (ancestor instanceof J.Lambda) {
                    return false;
                }
                if (ancestor instanceof J.MethodDeclaration) {
                    JavaType.Method methodType = ((J.MethodDeclaration) ancestor).getMethodType();
                    expected = methodType == null ? null : methodType.getReturnType();
                    break;
                }
            }
        } else if (parent instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) parent;
            expected = parameterType(mi.getMethodType(), mi.getArguments(), self);
        } else if (parent instanceof J.NewClass) {
            J.NewClass nc = (J.NewClass) parent;
            expected = parameterType(nc.getConstructorType(), nc.getArguments(), self);
        }
        if (expected == null) {
            return false;
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expected);
        return fq != null && COMPATIBLE_TARGETS.contains(fq.getFullyQualifiedName());
    }

    private static @Nullable JavaType parameterType(JavaType.@Nullable Method method, List<Expression> arguments, J self) {
        if (method == null) {
            return null;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i) == self) {
                List<JavaType> parameterTypes = method.getParameterTypes();
                return i < parameterTypes.size() ? parameterTypes.get(i) : null;
            }
        }
        return null;
    }
}
