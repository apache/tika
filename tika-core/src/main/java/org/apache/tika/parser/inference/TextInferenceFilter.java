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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * The {@link InputKind#TEXT} stage: runs the TEXT bindings over the finished metadata list of
 * a document tree, so every document's text goes to the engine in one pass and a batch may
 * hold chunks of the container and of its attachments alike. Appended to the metadata-filter
 * chain at config load when a TEXT binding exists. A document that released other inputs to
 * inference ({@link TikaCoreProperties#INFERENCE_RELEASED} without TEXT) is skipped. Failures
 * mark the first document of the list and never fail the filter chain.
 *
 * @since Apache Tika 4.1
 */
public class TextInferenceFilter extends MetadataFilter {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TextInferenceFilter.class);

    private final transient InferenceDispatcher dispatcher;

    public TextInferenceFilter(InferenceDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    protected void doFilter(List<Metadata> metadataList, ParseContext context)
            throws TikaException {
        if (metadataList.isEmpty()) {
            return;
        }
        Metadata root = metadataList.get(0);
        for (InferenceDispatcher.Bound b : dispatcher.running(InputKind.TEXT, context)) {
            InferenceBinding binding = b.binding();
            List<InferenceUnit> units = new ArrayList<>();
            int skipped = 0;
            for (Metadata metadata : metadataList) {
                String text = metadata.get(TikaCoreProperties.TIKA_CONTENT);
                if (text == null || text.isBlank() || !releasedText(metadata)) {
                    continue;
                }
                MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
                if (!binding.accepts(InputKind.TEXT,
                        type == null ? MediaType.OCTET_STREAM : type)) {
                    continue;
                }
                if (binding.getMaxBytes() >= 0
                        && text.getBytes(StandardCharsets.UTF_8).length > binding.getMaxBytes()) {
                    skipped++;
                    continue;
                }
                units.add(new InferenceUnit(type, metadata, null, text));
            }
            if (skipped > 0) {
                root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, "inference binding "
                        + binding.getId() + " over maxBytes: skipped " + skipped + " units");
            }
            if (units.isEmpty()) {
                continue;
            }
            for (InferenceTask task : b.tasks()) {
                try {
                    task.run(binding, units, b.engine(), context);
                } catch (Exception e) {
                    LOG.warn("inference binding {} failed", binding.getId(), e);
                    root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            "inference binding " + binding.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    /** Absent means the parser released its text; a list without TEXT means it did not. */
    private static boolean releasedText(Metadata metadata) {
        String[] released = metadata.getValues(TikaCoreProperties.INFERENCE_RELEASED);
        if (released.length == 0) {
            return true;
        }
        for (String r : released) {
            if (InputKind.TEXT.name().equals(r)) {
                return true;
            }
        }
        return false;
    }
}
