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
package org.apache.tika.extractor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

/**
 * TIKA-4808 -- a media type carrying parameters must still resolve to the
 * extension of its base type.
 */
public class EmbeddedDocumentUtilExtensionTest {

    @Test
    public void testParametersDoNotSuppressExtension() {
        //the corpus regression: Pkcs7Parser refines the coarse family label to the
        //exact smime-type, which turned /embedded-2.p7s into /embedded-2
        assertEquals(".p7m",
                EmbeddedDocumentUtil.getExtensionForMediaType(
                        "application/pkcs7-mime; smime-type=signed-data"));
        assertEquals(".txt",
                EmbeddedDocumentUtil.getExtensionForMediaType("text/plain; charset=UTF-8"));
        assertEquals(".js",
                EmbeddedDocumentUtil.getExtensionForMediaType(
                        "text/javascript; charset=UTF-8"));
        assertEquals(".css",
                EmbeddedDocumentUtil.getExtensionForMediaType(
                        "text/css; charset=ISO-2022-JP"));
        assertEquals(".html",
                EmbeddedDocumentUtil.getExtensionForMediaType(
                        "text/html; charset=windows-1252"));
    }

    @Test
    public void testUnparameterizedStillWorks() {
        assertEquals(".p7s",
                EmbeddedDocumentUtil.getExtensionForMediaType("application/pkcs7-signature"));
        assertEquals(".png", EmbeddedDocumentUtil.getExtensionForMediaType("image/png"));
        assertEquals(".txt", EmbeddedDocumentUtil.getExtensionForMediaType("text/plain"));
    }

    /**
     * A registered type that genuinely has parameters must win over its base type.
     */
    @Test
    public void testRegisteredParameterizedTypeWinsOverBaseType() {
        assertEquals(".ditamap",
                EmbeddedDocumentUtil.getExtensionForMediaType(
                        "application/dita+xml;format=map"));
        assertEquals(".dita",
                EmbeddedDocumentUtil.getExtensionForMediaType(
                        "application/dita+xml;format=topic"));
    }

    @Test
    public void testUnknownAndNull() {
        assertEquals("", EmbeddedDocumentUtil.getExtensionForMediaType(null));
        assertEquals("",
                EmbeddedDocumentUtil.getExtensionForMediaType("application/tika-bogus-xyz"));
    }

    @Test
    public void testOcrRoutingTypeIsNormalized() {
        assertEquals(".png", EmbeddedDocumentUtil.getExtensionForMediaType("image/ocr-png"));
    }

    /**
     * The lookup must not register anything: forName() would add one glob-less entry
     * per distinct parameter value seen, which on a large crawl grows without bound.
     */
    @Test
    public void testLookupDoesNotPolluteRegistry() {
        MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
        int before = mimeTypes.getMediaTypeRegistry().getTypes().size();
        for (int i = 0; i < 50; i++) {
            EmbeddedDocumentUtil.getExtensionForMediaType("text/plain; charset=made-up-" + i);
        }
        assertEquals(before, mimeTypes.getMediaTypeRegistry().getTypes().size());
        assertEquals(".txt",
                EmbeddedDocumentUtil.getExtensionForMediaType("text/plain; charset=made-up-0"));
    }

    @Test
    public void testGeneratedResourceNameKeepsExtension() {
        assertEquals("embedded-2.p7m", EmbeddedDocumentUtil.generateResourceName(
                EmbeddedDocumentUtil.EmbeddedResourcePrefix.EMBEDDED, 2,
                "application/pkcs7-mime; smime-type=signed-data"));
        assertEquals("image-0.png", EmbeddedDocumentUtil.generateResourceName(
                EmbeddedDocumentUtil.EmbeddedResourcePrefix.IMAGE, 0, "image/png"));
    }

    /**
     * A declared type that simply has no glob must not trigger detection -- that would
     * overwrite a CONTENT_TYPE the calling parser set deliberately.
     */
    @Test
    public void testDeclaredUnregisteredTypeIsNotOverwritten() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/tika-bogus-xyz");
        EmbeddedDocumentUtil util = new EmbeddedDocumentUtil(new ParseContext());
        try (TikaInputStream tis = TikaInputStream.get("%PDF-1.4\n".getBytes(UTF_8))) {
            assertEquals("", util.getExtension(tis, metadata));
            assertEquals("application/tika-bogus-xyz", metadata.get(Metadata.CONTENT_TYPE));
        }
    }

    @Test
    public void testDeclaredParameterizedTypeResolvesAndIsPreserved() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain; charset=UTF-8");
        EmbeddedDocumentUtil util = new EmbeddedDocumentUtil(new ParseContext());
        try (TikaInputStream tis = TikaInputStream.get("hello".getBytes(UTF_8))) {
            assertEquals(".txt", util.getExtension(tis, metadata));
            assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));
        }
    }

    /**
     * Guard the assumption the fix rests on: normalize() deliberately preserves
     * parameters, which is why forName() misses the registry for parameterized names.
     */
    @Test
    public void testNormalizePreservesParameters() {
        MediaType withParams = MediaType.parse("text/plain; charset=UTF-8");
        assertEquals(withParams,
                MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().normalize(withParams));
    }
}
