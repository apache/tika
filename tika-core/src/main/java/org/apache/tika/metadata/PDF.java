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
package org.apache.tika.metadata;

/**
 * PDF properties collection.
 *
 * @since Apache Tika 1.14
 */
public interface PDF {

    String PDF_PREFIX = "pdf"+Metadata.NAMESPACE_PREFIX_DELIMITER;
    String PDFA_PREFIX = "pdfa"+Metadata.NAMESPACE_PREFIX_DELIMITER;
    String PDFAID_PREFIX = "pdfaid"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Prefix to be used for properties that record what was stored
     * in the docinfo section (as opposed to XMP)
     */
    String PDF_DOC_INFO_PREFIX = PDF_PREFIX + "docinfo" +
            Metadata.NAMESPACE_PREFIX_DELIMITER;

    String PDF_DOC_INFO_CUSTOM_PREFIX = PDF_DOC_INFO_PREFIX+"custom"+
            Metadata.NAMESPACE_PREFIX_DELIMITER;

    Property DOC_INFO_CREATED = Property.internalDate(PDF_DOC_INFO_PREFIX + "created");

    Property DOC_INFO_CREATOR = Property.internalText(PDF_DOC_INFO_PREFIX + "creator");

    Property DOC_INFO_CREATOR_TOOL = Property.internalText(PDF_DOC_INFO_PREFIX + "creator_tool");

    Property DOC_INFO_MODIFICATION_DATE = Property.internalDate(PDF_DOC_INFO_PREFIX + "modified");

    Property DOC_INFO_KEY_WORDS = Property.internalText(PDF_DOC_INFO_PREFIX + "keywords");

    Property DOC_INFO_PRODUCER = Property.internalText(PDF_DOC_INFO_PREFIX + "producer");

    Property DOC_INFO_SUBJECT = Property.internalText(PDF_DOC_INFO_PREFIX + "subject");

    Property DOC_INFO_TITLE = Property.internalText(PDF_DOC_INFO_PREFIX + "title");

    Property DOC_INFO_TRAPPED = Property.internalText(PDF_DOC_INFO_PREFIX + "trapped");

    Property PDF_VERSION = Property.internalRational(PDF_PREFIX+"PDFVersion");
    Property PDFA_VERSION = Property.internalRational(PDFA_PREFIX+"PDFVersion");
    Property PDF_EXTENSION_VERSION = Property.internalRational(PDF_PREFIX+"PDFExtensionVersion");

    Property PDFAID_CONFORMANCE = Property.internalText(PDFAID_PREFIX+"conformance");

    Property PDFAID_PART = Property.internalText(PDFAID_PREFIX+"part");

    Property IS_ENCRYPTED = Property.internalBoolean(PDF_PREFIX+"encrypted");

    /**
     * This specifies where an action or destination would be found/triggered
     * in the document: on document open, before close, etc.
     */
    Property ACTION_TRIGGER = Property.internalText(PDF_PREFIX+"actionTrigger");
}
