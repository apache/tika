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
package org.apache.tika.parser.exiftool;

import java.util.List;
import java.util.Map;

import org.apache.tika.metadata.Property;

/**
 * Defines method to convert to and from Tika metadata properties
 * and ExifTool metadata fields.
 */
public interface ExiftoolTikaMapper {

    /**
     * Gets a map of Tika metadata names to an array of ExifTool metadata names. Most
     * useful for constructing command line arguments.
     *
     * Multiple ExifTool metadata names are provided since it is commonplace to write the
     * same general, Tika metadata value to several metadata fields.  For example,
     * a copyright notice Tika field might be written to EXIF, legacy IPTC, and
     * XMP.
     *
     * @return the map of Tika metadata names to ExifTool names
     */
    public Map<Property, List<Property>> getTikaToExiftoolMetadataMap();

    /**
     * Gets a map of ExifTool metadata names to a single Tika metadata name. Most
     * useful for parsers.
     *
     * @return the map of ExifTool metadata names to Tika names
     */
    public Map<Property, List<Property>> getExiftoolToTikaMetadataMap();

}
