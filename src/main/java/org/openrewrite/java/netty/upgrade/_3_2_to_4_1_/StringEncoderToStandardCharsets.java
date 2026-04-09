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
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

public class StringEncoderToStandardCharsets extends Recipe {

    private static final Map<String, String> CHARSET_MAPPING;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("US-ASCII", "US_ASCII");
        map.put("ISO-8859-1", "ISO_8859_1");
        map.put("UTF-8", "UTF_8");
        map.put("UTF-16BE", "UTF_16BE");
        map.put("UTF-16LE", "UTF_16LE");
        map.put("UTF-16", "UTF_16");
        CHARSET_MAPPING = unmodifiableMap(map);
    }

    @Getter
    final String displayName = "Migrate StringEncoder(String) to StringEncoder(StandardCharsets)";

    @Getter
    final String description = "Replaces `new StringEncoder(charsetName)` with `new StringEncoder(StandardCharsets.<constant>)` " +
            "for all standard charsets (US-ASCII, ISO-8859-1, UTF-8, UTF-16BE, UTF-16LE, UTF-16).";

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);

                if (!(nc.getType() instanceof JavaType.Class)) {
                    return nc;
                }
                JavaType.Class type = (JavaType.Class) nc.getType();

                if (!"org.jboss.netty.handler.codec.string.StringEncoder".equals(type.getFullyQualifiedName()) ||
                    nc.getArguments() == null ||
                    nc.getArguments().size() != 1 ||
                    !(nc.getArguments().get(0) instanceof J.Literal)) {
                    return nc;
                }
                J.Literal literal = (J.Literal) nc.getArguments().get(0);

                if (!(literal.getValue() instanceof String)) {
                    return nc;
                }
                String charsetName = (String) literal.getValue();

                String constantName = CHARSET_MAPPING.get(charsetName);
                if (constantName == null) {
                    return nc;
                }

                maybeRemoveImport("org.jboss.netty.handler.codec.string.StringEncoder");
                maybeAddImport("io.netty.handler.codec.string.StringEncoder");
                maybeAddImport("java.nio.charset.StandardCharsets");

                JavaTemplate template = JavaTemplate.builder(
                                "new StringEncoder(StandardCharsets." + constantName + ")")
                        .imports("io.netty.handler.codec.string.StringEncoder")
                        .imports("java.nio.charset.StandardCharsets")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "netty-codec-base"))
                        .build();

                return template.apply(
                        updateCursor(nc),
                        nc.getCoordinates().replace()
                );
            }
        };
    }
}
