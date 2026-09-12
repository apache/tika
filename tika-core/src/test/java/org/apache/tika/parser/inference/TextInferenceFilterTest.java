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
package org.apache.tika.parser.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class TextInferenceFilterTest {

    static final class RecordingTask implements InferenceTask {
        final List<List<InferenceUnit>> runs = new ArrayList<>();

        @Override
        public void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
                        ParseContext context) {
            runs.add(List.copyOf(units));
        }
    }

    private static InferenceBinding text(String id, Set<MediaType> include, long maxBytes) {
        return new InferenceBinding(id, "engine", InputKind.TEXT, List.of("t"), include, null,
                -1, maxBytes, true);
    }

    private static Metadata doc(String type, String content) {
        Metadata m = new Metadata();
        m.set(HttpHeaders.CONTENT_TYPE, type);
        if (content != null) {
            m.set(TikaCoreProperties.TIKA_CONTENT, content);
        }
        return m;
    }

    @Test
    public void testEveryDocumentWithTextIsOneUnitOfOneRun() throws Exception {
        RecordingTask task = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(text("t", null, -1), new Engine() { },
                        List.of(task))));
        Metadata root = doc("application/zip", "root text");
        Metadata child = doc("text/plain", "child text");
        Metadata blank = doc("image/png", "  ");
        Metadata pages = doc("application/pdf", "pdf text");
        pages.add(TikaCoreProperties.INFERENCE_RELEASED, "PAGES");
        List<Metadata> list = List.of(root, child, blank, pages);

        new TextInferenceFilter(dispatcher).filter(list, new ParseContext());

        assertEquals(1, task.runs.size(), "the whole list in one run");
        List<InferenceUnit> units = task.runs.get(0);
        assertEquals(2, units.size(), "blank content and a document released as pages are skipped");
        assertSame(root, units.get(0).getTarget());
        assertEquals("root text", units.get(0).getText());
        assertEquals(InputKind.TEXT, units.get(0).getKind());
        assertSame(child, units.get(1).getTarget());
        assertEquals(MediaType.TEXT_PLAIN, units.get(1).getType());
    }

    @Test
    public void testFiltersBudgetAndSelection() throws Exception {
        RecordingTask plain = new RecordingTask();
        RecordingTask small = new RecordingTask();
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(text("plain", Set.of(MediaType.TEXT_PLAIN), -1),
                        new Engine() { }, List.of(plain)),
                new InferenceDispatcher.Bound(text("small", null, 5), new Engine() { },
                        List.of(small))));
        Metadata root = doc("application/zip", "root text");
        Metadata child = doc("text/plain", "abc");
        List<Metadata> list = List.of(root, child);

        new TextInferenceFilter(dispatcher).filter(list, new ParseContext());
        assertEquals(1, plain.runs.get(0).size(), "_mime-include narrows by document type");
        assertSame(child, plain.runs.get(0).get(0).getTarget());
        assertEquals(1, small.runs.get(0).size(), "maxBytes drops the 9-byte root");
        assertTrue(root.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("small over maxBytes: skipped 1 units"));

        ParseContext off = new ParseContext();
        InferenceSelection selection = new InferenceSelection();
        selection.setEnabled(false);
        off.set(InferenceSelection.class, selection);
        new TextInferenceFilter(dispatcher).filter(List.of(doc("text/plain", "x")), off);
        assertEquals(1, plain.runs.size(), "switched off for the request: no run");
    }

    @Test
    public void testTaskFailureIsAWarningOnTheRoot() throws Exception {
        InferenceTask broken = (binding, units, engine, context) -> {
            throw new IllegalStateException("boom");
        };
        InferenceDispatcher dispatcher = new InferenceDispatcher(List.of(
                new InferenceDispatcher.Bound(text("t", null, -1), new Engine() { },
                        List.of(broken))));
        Metadata root = doc("text/plain", "hello");
        new TextInferenceFilter(dispatcher).filter(List.of(root), new ParseContext());
        assertTrue(root.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("inference binding t: boom"));
    }
}
