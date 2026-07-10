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
package org.apache.tika.grpc.mapper.transform;

import java.util.Locale;
import java.util.Set;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.DocumentMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPRights;

/**
 * Creative Commons / XMP rights detection. Unlike the format transformers, this one is
 * cross-cutting: Creative Commons or other rights-management metadata can ride along with
 * any document format (a PDF, a JPEG, an Office doc, ...), so it does not key off
 * content-type at all. Instead it inspects the full metadata for CC/license-shaped field
 * names and values, or for any populated XMP rights-management property.
 *
 * <p>Rights-management specifics (marked, owner, usage terms, certificate, ...) are NOT
 * given their own proto fields. They flow into the tagged tail, typed where Tika declares
 * the type and string otherwise - same philosophy as the format transformers.
 */
public final class CreativeCommonsDocumentTransformer implements DocumentTransformer {

    private static final String[] NAME_HINTS = {"license", "creative", "cc:", "rights"};

    private static final Property[] XMP_RIGHTS_PROPERTIES = {
            XMPRights.MARKED,
            XMPRights.OWNER,
            XMPRights.USAGE_TERMS,
            XMPRights.WEB_STATEMENT,
            XMPRights.CERTIFICATE
    };

    @Override
    public boolean isCrossCutting() {
        return true;
    }

    @Override
    public boolean appliesTo(Metadata tika) {
        for (String name : tika.names()) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            for (String hint : NAME_HINTS) {
                if (lowerName.contains(hint)) {
                    String value = tika.get(name);
                    if (value != null && value.toLowerCase(Locale.ROOT).contains("creative")) {
                        return true;
                    }
                    break;
                }
            }
        }

        for (Property property : XMP_RIGHTS_PROPERTIES) {
            String value = tika.get(property);
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void transform(Metadata tika, Document.Builder document, Set<String> consumed) {
        DocumentMetadata.Builder meta = document.getMetadataBuilder();

        TransformSupport.setString(tika, TikaCoreProperties.RIGHTS, meta::setRights, consumed);
        // A CC license URL, e.g. https://creativecommons.org/licenses/by/4.0/, typically
        // lives in the XMP rights "web statement" property.
        TransformSupport.setString(tika, XMPRights.WEB_STATEMENT, meta::setLicenseUrl, consumed);

        // Everything else (xmpRights:Marked, xmpRights:Owner, xmpRights:UsageTerms,
        // xmpRights:Certificate, ...) lands in the tagged tail, appended once by
        // DocumentTransformers.
    }
}
