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
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Images. Maps the common, cross-format facts into typed DocumentMetadata.
 *
 * Image-specific properties (EXIF exposure/f-number/focal-length/flash/ISO, GPS
 * coordinates, IPTC headline/category/credit-line/copyright-notice, Photoshop
 * city/country/state, equipment make/model, resolution, orientation, ...) are NOT
 * given their own proto fields. They flow into the tagged tail, typed where Tika
 * declares the type and string otherwise. That is the whole point: format richness
 * lives in code plus the tail, not in the wire contract - so the schema stays small
 * and stable and clients never rebuild when we add or change an image property.
 */
public final class ImageDocumentTransformer implements DocumentTransformer {

    @Override
    public boolean appliesTo(Metadata tika) {
        String contentType = tika.get(Metadata.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    @Override
    public void transform(Metadata tika, Document.Builder document, Set<String> consumed) {
        DocumentMetadata.Builder meta = document.getMetadataBuilder();

        // Images deliberately do NOT use TransformSupport.mapCommonFields: their common
        // facts come from image-native properties (IPTC keywords, EXIF/TIFF original date),
        // and dc:creator / dc:language / dcterms:created rarely mean for a photo what they
        // mean for a document, so those stay in the tagged tail untouched.
        TransformSupport.setString(tika, TikaCoreProperties.TITLE, meta::setTitle, consumed);
        TransformSupport.setString(tika, TikaCoreProperties.DESCRIPTION, meta::setDescription, consumed);
        TransformSupport.addStrings(tika, IPTC.KEYWORDS, meta::addAllKeywords, consumed);
        TransformSupport.setTimestamp(tika, TIFF.ORIGINAL_DATE, meta::setCreated, consumed);
        TransformSupport.setTimestamp(tika, TikaCoreProperties.MODIFIED, meta::setModified, consumed);
        TransformSupport.setInt(tika, TIFF.IMAGE_WIDTH, meta::setWidth, consumed);
        TransformSupport.setInt(tika, TIFF.IMAGE_LENGTH, meta::setHeight, consumed);

        // Everything image-specific (exif:*, tiff:*, IPTC-*, photoshop:*, geo:*, ...) lands
        // in the tagged tail, appended once by DocumentTransformers.
    }
}
