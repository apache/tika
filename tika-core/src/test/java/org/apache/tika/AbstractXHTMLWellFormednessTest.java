/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;

/**
 * Nightly-only sweep: parses every file in the current module's
 * {@code src/test/resources/test-documents/} (recursively) with
 * {@link #AUTO_DETECT_PARSER}, captures the XHTML output, and asserts it is
 * well-formed via {@link #assertValidXHTML}. Parse exceptions are tolerated
 * (encrypted/corrupt files are out of scope here); whatever XHTML was emitted
 * before the failure is still validated.
 * <p>
 * Tagged {@code xhtml-validation}; excluded from normal builds by surefire
 * config in tika-parent. Activate with {@code ./mvnw -Pxhtml-validation test}.
 * <p>
 * To wire into a module, drop a concrete subclass into the module's test
 * sources — the inherited {@link #xhtmlIsWellFormed()} will walk that module's
 * own {@code test-documents/}, since surefire runs from the module basedir.
 */
@Tag("xhtml-validation")
public abstract class AbstractXHTMLWellFormednessTest extends TikaTest {

    private static final Path TEST_DOCUMENTS =
            Paths.get("src/test/resources/test-documents").toAbsolutePath();

    @TestFactory
    public Stream<DynamicTest> xhtmlIsWellFormed() throws Exception {
        return Files.walk(TEST_DOCUMENTS)
                .filter(Files::isRegularFile)
                .filter(this::shouldTest)
                .sorted()
                .map(file -> DynamicTest.dynamicTest(
                        TEST_DOCUMENTS.relativize(file).toString(),
                        () -> validate(file)));
    }

    /**
     * Override to skip specific files. Default excludes the {@code mock/}
     * subtree, which holds failure-injection fixtures (deliberate OOMs, NPEs,
     * hangs, etc.) used by parser failure-mode tests rather than real content.
     */
    protected boolean shouldTest(Path file) {
        return !TEST_DOCUMENTS.relativize(file).toString().startsWith("mock/");
    }

    private static void validate(Path file) throws Exception {
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            AUTO_DETECT_PARSER.parse(tis, handler, new Metadata(), new ParseContext());
        } catch (Exception ignored) {
            // Parse failures (e.g., encrypted documents) are out of scope here; we
            // still want to validate whatever XHTML was emitted before the failure.
        }
        assertValidXHTML(handler.toString());
    }
}
