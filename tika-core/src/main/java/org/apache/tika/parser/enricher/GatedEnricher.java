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
package org.apache.tika.parser.enricher;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * A text recognizer behind a chain of {@link ImageCheck}s, in order. The first check to refuse
 * keeps the image from the engine and records {@code skipped:<reason>}; a check that fails
 * admits it. One decorator however many checks an entry lists.
 *
 * @since Apache Tika 4.2
 */
public class GatedEnricher extends ParserDecorator {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(GatedEnricher.class);

    private final List<ImageCheck> checks;

    public GatedEnricher(Parser parser, List<ImageCheck> checks) {
        super(parser);
        this.checks = List.copyOf(checks);
    }

    public List<ImageCheck> getChecks() {
        return checks;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        ImageProbe probe = new ImageProbe(tis, metadata);
        for (ImageCheck check : checks) {
            ImageCheck.Verdict verdict;
            try {
                verdict = check.check(probe, metadata, context);
            } catch (IOException | RuntimeException e) {
                LOG.debug("{} could not judge the image; admitting it", check.getClass(), e);
                continue;
            }
            if (!verdict.admitted()) {
                OcrWarnings.add(context, metadata, "skipped:" + verdict.reason());
                return;
            }
        }
        super.parse(tis, handler, metadata, context);
    }

    @Override
    public String getDecorationName() {
        return "Gated (" + checks.size() + " checks)";
    }
}
