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
package org.apache.tika.detect.crypto;

import java.io.IOException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.crypto.CmsClassifier;

/**
 * Optional detector that surfaces the CMS/PKCS7 {@code smime-type} at detection time, using the
 * same {@link CmsClassifier} as {@code Pkcs7Parser}. Marked {@code spi = false} so it is NOT
 * auto-loaded (the default detect path stays cheap — magic gives the coarse family and the parser
 * refines the subtype); enable it by name in a tika-config when you need the subtype from
 * {@code detect()} without parsing.
 */
@TikaComponent(spi = false)
public class Pkcs7Detector implements Detector {

    private static final long serialVersionUID = 6879398011936510897L;

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }
        MediaType type = CmsClassifier.classify(tis);
        return type != null ? type : MediaType.OCTET_STREAM;
    }
}
