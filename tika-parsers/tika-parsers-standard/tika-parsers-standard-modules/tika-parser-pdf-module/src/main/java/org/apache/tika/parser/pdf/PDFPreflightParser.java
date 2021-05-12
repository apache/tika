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

package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdfparser.XrefTrailerResolver;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.preflight.Format;
import org.apache.pdfbox.preflight.PreflightConfiguration;
import org.apache.pdfbox.preflight.PreflightConstants;
import org.apache.pdfbox.preflight.PreflightContext;
import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.exception.SyntaxValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.ExceptionUtils;

public class PDFPreflightParser extends PDFParser {

    private static final PDFPreflightParserConfig DEFAULT = new PDFPreflightParserConfig();

    /**
     * Copied verbatim from PDFBox
     * <p>
     * According to the PDF Reference, A linearized PDF contain a dictionary as first object
     * (linearized dictionary) and
     * only this one in the first section.
     *
     * @param document the document to validate.
     * @return the linearization dictionary or null.
     */
    protected static COSDictionary getLinearizedDictionary(PDDocument document) {
        // ---- Get Ref to obj
        COSDocument cDoc = document.getDocument();
        List<?> lObj = cDoc.getObjects();
        for (Object object : lObj) {
            COSBase curObj = ((COSObject) object).getObject();
            if (curObj instanceof COSDictionary && ((COSDictionary) curObj).keySet()
                    .contains(COSName.getPDFName(PreflightConstants.DICTIONARY_KEY_LINEARIZED))) {
                return (COSDictionary) curObj;
            }
        }
        return null;
    }

    @Override
    protected PDDocument getPDDocument(InputStream inputStream, String password,
                                       MemoryUsageSetting memoryUsageSetting, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(inputStream)) {
            return getPDDocument(tis.getPath(), password, memoryUsageSetting, metadata,
                    parseContext);
        }
    }

    @Override
    protected PDDocument getPDDocument(Path path, String password,
                                       MemoryUsageSetting memoryUsageSetting, Metadata metadata,
                                       ParseContext context) throws IOException {
        PDFPreflightParserConfig pppConfig = context.get(PDFPreflightParserConfig.class, DEFAULT);

        PreflightConfiguration configuration = new PreflightConfiguration();
        configuration.setMaxErrors(pppConfig.getMaxErrors());
        PreflightParser preflightParser = new PreflightParser(path.toFile());

        preflightParser.setLenient(pppConfig.isLenient);
        try {
            preflightParser.parse(pppConfig.getFormat(), configuration);
        } catch (SyntaxValidationException e) {
            //back off to try to load the file normally
            return handleSyntaxException(path, password, memoryUsageSetting, metadata, e);
        }

        PreflightDocument preflightDocument = preflightParser.getPreflightDocument();
        preflightDocument.validate();
        extractPreflight(preflightDocument, metadata);

        //need to return this to ensure that it gets closed
        //the preflight document can keep some other resources open.
        return preflightParser.getPreflightDocument();
    }

    private void extractPreflight(PreflightDocument preflightDocument, Metadata metadata) {
        ValidationResult result = preflightDocument.getResult();
        metadata.set(PDF.PREFLIGHT_SPECIFICATION, preflightDocument.getSpecification().toString());
        metadata.set(PDF.PREFLIGHT_IS_VALID, Boolean.toString(result.isValid()));


        List<ValidationResult.ValidationError> errors = result.getErrorsList();
        for (ValidationResult.ValidationError err : errors) {
            metadata.add(PDF.PREFLIGHT_VALIDATION_ERRORS,
                    err.getErrorCode() + " : " + err.getDetails());
        }

        PreflightContext preflightContext = preflightDocument.getContext();

        XrefTrailerResolver resolver = preflightContext.getXrefTrailerResolver();
        int trailerCount = resolver.getTrailerCount();

        metadata.set(PDF.PREFLIGHT_TRAILER_COUNT, trailerCount);
        metadata.set(PDF.PREFLIGHT_XREF_TYPE, resolver.getXrefType().toString());
        if (preflightContext.getIccProfileWrapper() != null &&
                preflightContext.getIccProfileWrapper().getProfile() != null) {
            metadata.set(PDF.PREFLIGHT_ICC_PROFILE,
                    preflightContext.getIccProfileWrapper().getProfile().toString());
        }
        COSDictionary linearized = getLinearizedDictionary(preflightDocument);
        if (linearized != null) {
            metadata.set(PDF.PREFLIGHT_IS_LINEARIZED, "true");
            if (trailerCount > 2) {
                metadata.set(PDF.PREFLIGHT_INCREMENTAL_UPDATES, "true");
            } else {
                metadata.set(PDF.PREFLIGHT_INCREMENTAL_UPDATES, "false");
            }
        } else {
            metadata.set(PDF.PREFLIGHT_IS_LINEARIZED, "false");
            if (trailerCount > 1) {
                metadata.set(PDF.PREFLIGHT_INCREMENTAL_UPDATES, "true");
            } else {
                metadata.set(PDF.PREFLIGHT_INCREMENTAL_UPDATES, "false");
            }
        }
    }

    private PDDocument handleSyntaxException(Path path, String password,
                                             MemoryUsageSetting memoryUsageSetting,
                                             Metadata metadata, SyntaxValidationException e)
            throws IOException {
        metadata.add(PDF.PREFLIGHT_PARSE_EXCEPTION, ExceptionUtils.getStackTrace(e));
        return PDDocument.load(path.toFile(), password, memoryUsageSetting);
    }

    private static class PDFPreflightParserConfig {
        private int maxErrors = 100;
        private boolean isLenient = true;
        private Format format = Format.PDF_A1B;

        public int getMaxErrors() {
            return maxErrors;
        }

        public boolean isLenient() {
            return isLenient;
        }

        public Format getFormat() {
            return format;
        }
    }
}
