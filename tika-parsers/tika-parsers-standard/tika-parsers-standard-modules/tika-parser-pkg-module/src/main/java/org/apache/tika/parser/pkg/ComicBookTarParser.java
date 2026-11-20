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

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.RequestConfigurable;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Parser for comic book archives in tar (.cbt). It reads the tar as {@link PackageParser}
 * does, in one pass over the stream; with {@link ComicBookTarParserConfig#isMarkCover()} it
 * also marks the cover, which costs a pass over the entry headers of the file first.
 */
@RequestConfigurable
@TikaComponent
public class ComicBookTarParser extends PackageParser {

    private static final long serialVersionUID = 1L;

    private static final String CONFIG_KEY = "comic-book-tar-parser";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(ComicBook.CBT);

    private final ComicBookTarParserConfig defaultConfig;

    public ComicBookTarParser() {
        this(new ComicBookTarParserConfig());
    }

    public ComicBookTarParser(ComicBookTarParserConfig config) {
        this.defaultConfig = config;
    }

    public ComicBookTarParser(JsonConfig jsonConfig) throws TikaConfigException {
        this(ConfigDeserializer.buildConfig(jsonConfig, ComicBookTarParserConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        ComicBookTarParserConfig config = ParseContextConfig.getConfig(context, CONFIG_KEY,
                ComicBookTarParserConfig.class, defaultConfig);
        if (!config.isMarkCover()) {
            super.parse(tis, handler, metadata, context);
            return;
        }
        String[] cover = {cover(tis, context)};
        parse(tis, handler, metadata, context, (entry, entrydata) -> {
            if (entry.getName().equals(cover[0])) {
                entrydata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
                cover[0] = null;
            }
        });
    }

    /** The first page in name order, from the entry headers alone; null if there is none. */
    private static String cover(TikaInputStream tis, ParseContext context) {
        String encoding = context.get(ArchiveStreamFactory.class, new ArchiveStreamFactory())
                .getEntryEncoding();
        String cover = null;
        // TarFile seeks from header to header; a TarArchiveInputStream reads the data too
        try (TarFile tar = new TarFile(tis.getPath(), encoding)) {
            for (TarArchiveEntry entry : tar.getEntries()) {
                if (!entry.isDirectory()) {
                    cover = ComicBook.earlier(cover, entry.getName());
                }
            }
        } catch (IOException e) {
            // no cover then; the parse reports what is wrong with the tar
        }
        return cover;
    }
}
