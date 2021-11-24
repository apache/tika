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
package org.apache.tika.fuzzing.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.fuzzing.exceptions.CantFuzzException;
import org.apache.tika.mime.MediaType;

public class PDFTransformer implements Transformer {
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("pdf"));
    private PDFTransformerConfig config = new PDFTransformerConfig();

    @Override
    public Set<MediaType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void transform(InputStream is, OutputStream os) throws IOException, TikaException {
        try (PDDocument pdDocument = PDDocument.load(is)) {
            //some docs have security which prevents mods and writing
            //given our purposes here, we should remove security
            pdDocument.setAllSecurityToBeRemoved(true);
            try (EvilCOSWriter cosWriter = new EvilCOSWriter(os, config)) {
                cosWriter.write(pdDocument);
            }
        } catch (InvalidPasswordException e) {
            throw new CantFuzzException("encrypted doc");
        }
    }

    public void setConfig(PDFTransformerConfig pdfTransformerConfig) {
        this.config = pdfTransformerConfig;
    }
}
