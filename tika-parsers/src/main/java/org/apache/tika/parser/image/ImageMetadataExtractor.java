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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.xml.sax.SAXException;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.tiff.TiffMetadataReader;
import com.drew.imaging.tiff.TiffProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;

public class ImageMetadataExtractor {

    private final Metadata metadata;

    public ImageMetadataExtractor(Metadata metadata) {
        this.metadata = metadata;
    }

    public void parseTiff(InputStream stream)
            throws IOException, SAXException, TikaException {
        try {
            com.drew.metadata.Metadata tiffMetadata =
                TiffMetadataReader.readMetadata(stream);
            parse(tiffMetadata);
        } catch (TiffProcessingException e) {
            throw new TikaException("Can't read TIFF metadata", e);
        }
    }
    
    public void parseJpeg(InputStream stream)
            throws IOException, SAXException, TikaException {
       try {
          com.drew.metadata.Metadata jpegMetadata =
             JpegMetadataReader.readMetadata(stream);
          parse(jpegMetadata);
       } catch (JpegProcessingException e) {
          throw new TikaException("Can't read JPEG metadata", e);
       }
    }
    
    protected void parse(com.drew.metadata.Metadata imageMetadata)
            throws IOException, SAXException, TikaException {
       try {
          Iterator<?> directories = imageMetadata.getDirectoryIterator();
          while (directories.hasNext()) {
             Directory directory = (Directory) directories.next();
             Iterator<?> tags = directory.getTagIterator();

             while (tags.hasNext()) {
                Tag tag = (Tag)tags.next();
                metadata.set(tag.getTagName(), tag.getDescription());
                handleCommonImageTags(metadata, tag);
             }
             handleGeoImageTags(metadata);
          }
       } catch (MetadataException e) {
          throw new TikaException("Can't read TIFF/JPEG metadata", e);
       }
    }

    /**
     * Maps EXIF Geo Tags onto the Tika Geo metadata namespace.
     * Needs to be run at the end, because the GPS information
     *  is spread across several EXIF tags.
     */
    public static void handleGeoImageTags(Metadata metadata) {
        String lat = metadata.get("GPS Latitude");
        String latNS = metadata.get("GPS Latitude Ref");
        if(lat != null) {
            Double latitude = parseHMS(lat);
            if(latitude != null) {
                if(latNS != null && latNS.equalsIgnoreCase("S") &&
                        latitude > 0) {
                    latitude *= -1;
                }
                metadata.set(Metadata.LATITUDE, LAT_LONG_FORMAT.format(latitude)); 
            }
        }

        String lng = metadata.get("GPS Longitude");
        String lngEW = metadata.get("GPS Longitude Ref");
        if(lng != null) {
            Double longitude = parseHMS(lng);
            if(longitude != null) {
                if(lngEW != null && lngEW.equalsIgnoreCase("W") &&
                        longitude > 0) {
                    longitude *= -1;
                }
                metadata.set(Metadata.LONGITUDE, LAT_LONG_FORMAT.format(longitude));
            }
        }
    }
    private static Double parseHMS(String hms) {
       Matcher m = HOURS_MINUTES_SECONDS.matcher(hms);
       if(m.matches()) {
          double value = 
            Integer.parseInt(m.group(1)) +
            (Integer.parseInt(m.group(2))/60.0) +
            (Double.parseDouble(m.group(3))/60.0/60.0);
          return value;
       }
       return null;
    }
    private static final Pattern HOURS_MINUTES_SECONDS = Pattern.compile("(-?\\d+)\"(\\d+)'(\\d+\\.?\\d*)");
    /**
     * The decimal format used for expressing latitudes and longitudes.
     * The basic geo vocabulary defined by W3C (@see {@link Geographic})
     * refers to the "float" type in XML Schema as the recommended format
     * for latitude and longitude values.
     */
    private static final DecimalFormat LAT_LONG_FORMAT =
        new DecimalFormat("##0.0####", new DecimalFormatSymbols(Locale.US));

    private static void handleDate(Metadata metadata, Property property, Tag tag) throws MetadataException {
       // Ensure it's in the right format
       String date = tag.getDescription();
       int splitAt = date.indexOf(' '); 
       if(splitAt > -1) {
           String datePart = date.substring(0, splitAt);
           String timePart = date.substring(splitAt+1);
           date = datePart.replace(':', '-') + 'T' + timePart;
       }
       metadata.set(property, date);
    }

    /**
     * Maps common TIFF and EXIF tags onto the Tika
     *  TIFF image metadata namespace.
     */
    public static void handleCommonImageTags(Metadata metadata, Tag tag) throws MetadataException {
        // Core tags
        if(tag.getTagName().equals("Date/Time") ||
                tag.getTagType() == 306) {
            handleDate(metadata, Metadata.DATE, tag);
            handleDate(metadata, Metadata.LAST_MODIFIED, tag);
            return;
        }
        if(tag.getTagName().equals("Date/Time Original") ||
              tag.getTagType() == 36867) {
          handleDate(metadata, Metadata.ORIGINAL_DATE, tag);
          return;
      }
        if(tag.getTagName().equals("Keywords") ||
                tag.getTagType() == 537) {
            metadata.set(Metadata.KEYWORDS, tag.getDescription());
            return;
        }
        if(tag.getTagName().equals("Jpeg Comment")) {
            metadata.set(Metadata.COMMENTS, tag.getDescription());
            return;
        }

        // File info
        // Metadata Extractor does not read XMP so we need to use the values from Iptc or EXIF
        if("Iptc".equals(tag.getDirectoryName())) {
            if("Object Name".equals(tag.getTagName())) {
                metadata.set(Metadata.TITLE, tag.getDescription());
                return;
            }
            if("By-line".equals(tag.getTagName())) {
                metadata.set(Metadata.AUTHOR, tag.getDescription());
                return;
            }		
            if("Caption/Abstract".equals(tag.getTagName())) {
                // Looks like metadata extractor returns IPTC newlines as a single carriage return,
                // but the exiv2 command does not so we change to line feed here because that is less surprising to users
                metadata.set(Metadata.DESCRIPTION, tag.getDescription().replaceAll("\r\n?", "\n"));
                return;
            }
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
