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
package org.apache.tika.parser.pkg;

import java.nio.file.Path;
import java.util.Set;

import org.apache.tika.AbstractXHTMLWellFormednessTest;

public class XHTMLWellFormednessTest extends AbstractXHTMLWellFormednessTest {

    // Quine fixtures: archives that contain themselves, used to test the
    // zip-bomb depth defense in SecureContentHandler. The defense bails out
    // mid-parse by design, so the partial XHTML is intentionally unterminated.
    private static final Set<String> QUINES = Set.of("droste.zip", "quine.gz");

    @Override
    protected boolean shouldTest(Path file) {
        return super.shouldTest(file) && !QUINES.contains(file.getFileName().toString());
    }
}
