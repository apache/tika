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
package org.apache.tika.parser.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.xml.sax.SAXException;

import com.drew.imaging.tiff.TiffMetadataReader;
import com.drew.imaging.tiff.TiffProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;

public class TiffExtractor {

    private final Metadata metadata;

    protected TiffExtractor(Metadata metadata) {
        this.metadata = metadata;
    }

    protected void parse(InputStream stream)
            throws IOException, SAXException, TikaException {
        try {
            com.drew.metadata.Metadata tiffMetadata =
                TiffMetadataReader.readMetadata(stream);

            Iterator<?> directories = tiffMetadata.getDirectoryIterator();
            while (directories.hasNext()) {
                Directory directory = (Directory) directories.next();
                Iterator<?> tags = directory.getTagIterator();

                while (tags.hasNext()) {
                    Tag tag = (Tag)tags.next();
                    metadata.set(tag.getTagName(), tag.getDescription());
                    handleCommonImageTags(metadata, tag);
                }
            }
        } catch (TiffProcessingException e) {
            throw new TikaException("Can't read TIFF metadata", e);
        } catch (MetadataException e) {
            throw new TikaException("Can't read TIFF metadata", e);
        }
    }


    /**
     * Maps common TIFF and EXIF tags onto the Tika
     *  TIFF image metadata namespace.
     */
    public static void handleCommonImageTags(Metadata metadata, Tag tag) throws MetadataException {
	// Core tags
	if(tag.getTagName().equals("Date/Time") ||
		tag.getTagType() == 306) {
	    // Ensure it's in the right format
	    String date = tag.getDescription();
	    int splitAt = date.indexOf(' '); 
	    if(splitAt > -1) {
		date = date.substring(0, splitAt).replace(':', '/') +
			date.substring(splitAt);
	    }
	    metadata.set(Metadata.DATE, date);
	    return;
	}
	if(tag.getTagName().equals("Keywords") ||
	        tag.getTagType() == 537) {
	    metadata.set(Metadata.KEYWORDS, tag.getDescription());
	}
	
	// EXIF / TIFF Tags
	Property key = null;
	if(tag.getTagName().equals("Image Width") ||
		tag.getTagType() == 256) { 
	    key = Metadata.IMAGE_WIDTH;
	}
	if(tag.getTagName().equals("Image Height") ||
		tag.getTagType() == 257) {
	    key = Metadata.IMAGE_LENGTH;
	}
	if(tag.getTagName().equals("Data Precision") ||
		tag.getTagName().equals("Bits Per Sample") ||
		tag.getTagType() == 258) {
	    key = Metadata.BITS_PER_SAMPLE;
	}
	if(tag.getTagType() == 277) {
	    key = Metadata.SAMPLES_PER_PIXEL;
	}
	
	if(key != null) {
	    Matcher m = LEADING_NUMBERS.matcher(tag.getDescription());
	    if(m.matches()) {
		metadata.set(key, m.group(1));
	    }
	}
    }
    private static final Pattern LEADING_NUMBERS = Pattern.compile("(\\d+)\\s*.*");
}
