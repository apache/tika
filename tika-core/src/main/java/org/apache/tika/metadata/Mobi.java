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
 * Keys of Mobipocket and Kindle books (.mobi, .prc, .azw, .azw3), from the MOBI header and
 * the EXTH records of record 0. The Dublin Core keys carry what EXTH has a standard name for.
 */
public interface Mobi {

    String MOBI_PREFIX = "mobi" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /** The MOBI header's file version: 6 and below is the classic format, 8 and above KF8. */
    Property VERSION = Property.externalInteger(MOBI_PREFIX + "version");

    /** How the text records are compressed. */
    Property COMPRESSION = Property.externalClosedChoice(MOBI_PREFIX + "compression",
            "none", "palmdoc", "huffcdic", "unknown");

    /** Whether the text is encrypted (Mobipocket DRM), and with which scheme. */
    Property ENCRYPTION = Property.externalClosedChoice(MOBI_PREFIX + "encryption",
            "none", "old-mobipocket", "mobipocket", "unknown");

    /**
     * The Kindle content type from EXTH 501, {@code EBOK} for a book, {@code PDOC} for a
     * personal document, {@code MAGZ} for a magazine and so on.
     */
    Property CDE_TYPE = Property.externalText(MOBI_PREFIX + "cde-type");

    /** Whether the file is a hybrid, a classic book with a KF8 book behind a boundary record. */
    Property HYBRID = Property.externalBoolean(MOBI_PREFIX + "hybrid");
}
