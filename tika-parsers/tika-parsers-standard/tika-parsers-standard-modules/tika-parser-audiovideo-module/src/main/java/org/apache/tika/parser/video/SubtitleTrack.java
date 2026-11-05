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
package org.apache.tika.parser.video;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.xml.sax.SAXException;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * One subtitle track, accumulated cue by cue as WebVTT and emitted as an embedded
 * {@code text/vtt} document. Made by {@link SubtitleTracks#add}.
 */
final class SubtitleTrack {

    private static final char LINE_SEPARATOR = 0x2028;

    private final SubtitleTracks owner;
    private final String name;
    private final String language;
    private final String title;
    private final StringBuilder vtt = new StringBuilder("WEBVTT\n");
    private int cues;

    SubtitleTrack(SubtitleTracks owner, String name, String language, String title) {
        this.owner = owner;
        this.name = name;
        this.language = language;
        this.title = title;
    }

    /**
     * Adds a cue; blank text is skipped (containers mark gaps with empty samples).
     *
     * @return false once the file's text budget is spent; later cues are dropped
     */
    boolean addCue(long startMillis, long endMillis, String text) {
        if (owner.isSpent()) {
            return false;
        }
        String body = cueText(text);
        if (body.isEmpty()) {
            return true;
        }
        int before = vtt.length();
        vtt.append('\n').append(time(startMillis)).append(" --> ")
                .append(time(Math.max(startMillis, endMillis))).append('\n')
                .append(body).append('\n');
        if (!owner.chargeChars(vtt.length() - before)) {
            vtt.setLength(before);
            return false;
        }
        cues++;
        return true;
    }

    void emit(XHTMLContentHandler xhtml, ParseContext context) throws IOException, SAXException {
        if (cues == 0) {
            return;
        }
        Metadata m = metadata(name, language, title, context);
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (extractor.shouldParseEmbedded(m, context)) {
            try (TikaInputStream tis =
                    TikaInputStream.get(vtt.toString().getBytes(StandardCharsets.UTF_8), m)) {
                extractor.parseEmbedded(tis, xhtml, m, context, true);
            }
        }
    }

    static Metadata metadata(String name, String language, String title, ParseContext context) {
        Metadata m = Metadata.newInstance(context);
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        m.set(HttpHeaders.CONTENT_TYPE, "text/vtt; charset=UTF-8");
        m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name());
        if (language != null) {
            m.set(TikaCoreProperties.LANGUAGE, language);
        }
        if (title != null && !title.isBlank()) {
            m.set(TikaCoreProperties.TITLE, title);
        }
        return m;
    }

    /** {@code HH:MM:SS.mmm}; hours grow past two digits as WebVTT allows. */
    static String time(long millis) {
        long ms = Math.max(0, millis);
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", ms / 3_600_000,
                (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000);
    }

    // escaped for WebVTT, and no blank line, which would end the cue
    static String cueText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean lineHasText = false;
        boolean pendingBreak = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u0000') {
                continue;
            }
            if (c == '\n' || c == '\r' || c == LINE_SEPARATOR) {
                pendingBreak |= lineHasText;
                lineHasText = false;
                continue;
            }
            if (!lineHasText && Character.isWhitespace(c)) {
                continue;
            }
            if (pendingBreak) {
                sb.append('\n');
                pendingBreak = false;
            }
            lineHasText = true;
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString().strip();
    }
}
