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
package org.apache.tika.detect.comicbook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;

/**
 * Detects comic book archives in RAR and tar, which a file name or declared type alone
 * cannot: the magic of a RAR is a versioned type (RAR4, RAR5) and that of a GNU tar is
 * application/x-gtar, and a name or type hint only replaces the magic type when it is a
 * sub-type of it. When the name ends in .cbr, .cb7 or .cbt, or the declared content type
 * is a comic type, and the content starts like the matching container, this returns the
 * comic type.
 */
@TikaComponent
public class ComicBookDetector implements Detector {

    private static final long serialVersionUID = 1L;

    static final MediaType CBR = MediaType.application("vnd.comicbook-rar");
    static final MediaType CB7 = MediaType.application("x-cb7");
    static final MediaType CBT = MediaType.application("x-cbt");

    private static final byte[] RAR = {'R', 'a', 'r', '!', 0x1a, 0x07};
    private static final byte[] SEVEN_Z = {'7', 'z', (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c};
    private static final byte[] USTAR = "ustar".getBytes(StandardCharsets.US_ASCII);
    private static final int USTAR_OFFSET = 257;

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }
        MediaType wanted = declared(metadata);
        if (wanted == null) {
            return MediaType.OCTET_STREAM;
        }
        int length = USTAR_OFFSET + USTAR.length;
        tis.mark(length);
        byte[] head;
        try {
            head = tis.readNBytes(length);
        } finally {
            tis.reset();
        }
        boolean matches = CBR.equals(wanted) ? startsWith(head, RAR, 0)
                : CB7.equals(wanted) ? startsWith(head, SEVEN_Z, 0)
                : startsWith(head, USTAR, USTAR_OFFSET);
        return matches ? wanted : MediaType.OCTET_STREAM;
    }

    /** The comic type the file name or the declared content type names, or null. */
    private static MediaType declared(Metadata metadata) {
        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".cbr")) {
                return CBR;
            } else if (lower.endsWith(".cb7")) {
                return CB7;
            } else if (lower.endsWith(".cbt")) {
                return CBT;
            }
        }
        MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        if (type != null) {
            type = MediaTypeRegistry.getDefaultRegistry().normalize(type.getBaseType());
            if (CBR.equals(type) || CB7.equals(type) || CBT.equals(type)) {
                return type;
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
