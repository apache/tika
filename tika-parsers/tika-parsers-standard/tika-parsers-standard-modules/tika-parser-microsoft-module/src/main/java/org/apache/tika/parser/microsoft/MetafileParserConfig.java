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
package org.apache.tika.parser.microsoft;

import java.io.Serializable;

import org.apache.tika.parser.pages.PagesConfig;

/**
 * Configuration of the {@link EMFParser} ("emf-parser") and the
 * {@link WMFParser} ("wmf-parser"): the parser's overlay on the {@code "pages"}
 * block. A metafile is one page; {@code pages.emit} rasterizes it and emits the
 * rendering as an embedded document, {@code pages.render} (under the
 * {@code emit.render} overlay) says how.
 */
public class MetafileParserConfig implements Serializable {

    private static final long serialVersionUID = -6371049153052164071L;

    private PagesConfig pages = new PagesConfig();

    public PagesConfig getPages() {
        return pages;
    }

    /** The overlay itself, to configure in code: {@code config.pages().emit().setEnabled(true)}. */
    public PagesConfig pages() {
        return pages;
    }

    public void setPages(PagesConfig pages) {
        this.pages = pages == null ? new PagesConfig() : pages;
    }
}
