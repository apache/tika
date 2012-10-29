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

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.xml.sax.SAXException;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.tiff.TiffMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jpeg.JpegCommentDirectory;
import com.drew.metadata.jpeg.JpegDirectory;

/**
 * Uses the <a href="http://www.drewnoakes.com/code/exif/">Metadata Extractor</a> library
 * to read EXIF and IPTC image metadata and map to Tika fields.
 * 
 * As of 2.4.0 the library supports jpeg and tiff.
 */
public class ImageMetadataExtractor {

    private final Metadata metadata;
    private DirectoryHandler[] handlers;
    private static final String GEO_DECIMAL_FORMAT_STRING = "#.######"; // 6 dp seems to be reasonable

    /**
     * @param metadata to extract to, using default directory handlers
     */
    public ImageMetadataExtractor(Metadata metadata) {
        this(metadata,
            new CopyUnknownFieldsHandler(),
            new JpegCommentHandler(),
            new ExifHandler(),
            new DimensionsHandler(),
            new GeotagHandler(),
            new IptcHandler()
        );
    }
    
    /**
     * @param metadata to extract to
     * @param handlers handlers in order, note that handlers may override values from earlier handlers
     */
    public ImageMetadataExtractor(Metadata metadata, DirectoryHandler... handlers) {
        this.metadata = metadata;
        this.handlers = handlers;
    }

    public void parseJpeg(File file)
            throws IOException, SAXException, TikaException {
        try {
            com.drew.metadata.Metadata jpegMetadata = JpegMetadataReader.readMetadata(file);
            handle(jpegMetadata);
        } catch (JpegProcessingException e) {
            throw new TikaException("Can't read JPEG metadata", e);
        } catch (MetadataException e) {
            throw new TikaException("Can't read JPEG metadata", e);
        }
    }

    protected void parseTiff(File file)
            throws IOException, SAXException, TikaException {
        try {
            com.drew.metadata.Metadata tiffMetadata = TiffMetadataReader.readMetadata(file);
            handle(tiffMetadata);
        } catch (MetadataException e) {
            throw new TikaException("Can't read TIFF metadata", e);
        }
    }

    /**
     * Copies extracted tags to tika metadata using registered handlers.
     * @param metadataExtractor Tag directories from a Metadata Extractor "reader"
     * @throws MetadataException This method does not handle exceptions from Metadata Extractor
     */
    protected void handle(com.drew.metadata.Metadata metadataExtractor) 
            throws MetadataException {
        handle(metadataExtractor.getDirectories().iterator());
    }

    /**
     * Copies extracted tags to tika metadata using registered handlers.
     * @param directories Metadata Extractor {@link com.drew.metadata.Directory} instances.
     * @throws MetadataException This method does not handle exceptions from Metadata Extractor
     */    
    protected void handle(Iterator<Directory> directories) throws MetadataException {
        while (directories.hasNext()) {
            Directory directory = directories.next();
            for (int i = 0; i < handlers.length; i++) {
                if (handlers[i].supports(directory.getClass())) {
                    handlers[i].handle(directory, metadata);
                }
            }
        }
    }

    /**
     * Reads one or more type of Metadata Extractor fields.
     */
    static interface DirectoryHandler {
        /**
         * @param directoryType A Metadata Extractor directory class
         * @return true if the directory type is supported by this handler
         */
        boolean supports(Class<? extends Directory> directoryType);
        /**
         * @param directory extracted tags
         * @param metadata current tika metadata
         * @throws MetadataException typically field extraction error, aborts all further extraction
         */
        void handle(Directory directory, Metadata metadata) 
                throws MetadataException;
    }

    /**
     * Mimics the behavior from TIKA-314 of copying all extracted tags
     * to tika metadata using field names from Metadata Extractor.
     */
    static class CopyAllFieldsHandler implements DirectoryHandler {
        public boolean supports(Class<? extends Directory> directoryType) {
            return true;
        }
        public void handle(Directory directory, Metadata metadata)
                throws MetadataException {
            if (directory.getTags() != null) {
                Iterator<?> tags = directory.getTags().iterator();
                while (tags.hasNext()) {
                    Tag tag = (Tag) tags.next();
                    metadata.set(tag.getTagName(), tag.getDescription());
                }
            }
        }
    }    
    
    /**
     * Copies all fields regardless of directory, if the tag name
     * is not identical to a known Metadata field name.
     * This leads to more predictable behavior than {@link CopyAllFieldsHandler}.
     */
    static class CopyUnknownFieldsHandler implements DirectoryHandler {
        public boolean supports(Class<? extends Directory> directoryType) {
            return true;
        }
        public void handle(Directory directory, Metadata metadata)
                throws MetadataException {
            if (directory.getTags() != null) {
                Iterator<?> tags = directory.getTags().iterator();
                while (tags.hasNext()) {
                    Tag tag = (Tag) tags.next();
                    String name = tag.getTagName();
                    if (!MetadataFields.isMetadataField(name) && tag.getDescription() != null) {
                          String value = tag.getDescription().trim();
                          if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
                              value = Boolean.TRUE.toString();
                          } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
                              value = Boolean.FALSE.toString();
                          }
                          metadata.set(name, value);
                    }
                }
            }
        }
    }
    
    /**
     * Basic image properties for TIFF and JPEG, at least.
     */
    static class DimensionsHandler implements DirectoryHandler {
        private final Pattern LEADING_NUMBERS = Pattern.compile("(\\d+)\\s*.*");
        public boolean supports(Class<? extends Directory> directoryType) {
            return directoryType == JpegDirectory.class || 
                        directoryType == ExifSubIFDDirectory.class ||
                        directoryType == ExifThumbnailDirectory.class ||
                        directoryType == ExifIFD0Directory.class;
        }
        public void handle(Directory directory, Metadata metadata) throws MetadataException {
            // The test TIFF has width and height stored as follows according to exiv2
            //Exif.Image.ImageWidth                        Short       1  100
            //Exif.Image.ImageLength                       Short       1  75
            // and the values are found in "Thumbnail Image Width" (and Height) from Metadata Extractor
            set(directory, metadata, ExifThumbnailDirectory.TAG_THUMBNAIL_IMAGE_WIDTH, Metadata.IMAGE_WIDTH);
            set(directory, metadata, JpegDirectory.TAG_JPEG_IMAGE_WIDTH, Metadata.IMAGE_WIDTH);
            set(directory, metadata, ExifThumbnailDirectory.TAG_THUMBNAIL_IMAGE_HEIGHT, Metadata.IMAGE_LENGTH);
            set(directory, metadata, JpegDirectory.TAG_JPEG_IMAGE_HEIGHT, Metadata.IMAGE_LENGTH);
            // Bits per sample, two methods of extracting, exif overrides jpeg
            set(directory, metadata, JpegDirectory.TAG_JPEG_DATA_PRECISION, Metadata.BITS_PER_SAMPLE);
            set(directory, metadata, ExifSubIFDDirectory.TAG_BITS_PER_SAMPLE, Metadata.BITS_PER_SAMPLE);
            // Straightforward
            set(directory, metadata, ExifSubIFDDirectory.TAG_SAMPLES_PER_PIXEL, Metadata.SAMPLES_PER_PIXEL);
        }
        private void set(Directory directory, Metadata metadata, int extractTag, Property metadataField) {
            if (directory.containsTag(extractTag)) {
                Matcher m = LEADING_NUMBERS.matcher(directory.getString(extractTag));
                if(m.matches()) {
                    metadata.set(metadataField, m.group(1));
                }
            }
        }
    }
    
    static class JpegCommentHandler implements DirectoryHandler {
        public boolean supports(Class<? extends Directory> directoryType) {
            return directoryType == JpegCommentDirectory.class;
        }
        public void handle(Directory directory, Metadata metadata) throws MetadataException {
            if (directory.containsTag(JpegCommentDirectory.TAG_JPEG_COMMENT)) {
                metadata.add(TikaCoreProperties.COMMENTS, directory.getString(JpegCommentDirectory.TAG_JPEG_COMMENT));
            }
        }
    }
    
    static class ExifHandler implements DirectoryHandler {
        private static final SimpleDateFormat DATE_UNSPECIFIED_TZ = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        public boolean supports(Class<? extends Directory> directoryType) {
            return directoryType == ExifIFD0Directory.class || 
                    directoryType == ExifSubIFDDirectory.class;
        }
        public void handle(Directory directory, Metadata metadata) {
            try {
                handleDateTags(directory, metadata);
                handlePhotoTags(directory, metadata);
                handleCommentTags(directory, metadata);
            } catch (MetadataException e) {
                // ignore date parse errors and proceed with other tags
            }
        }
        /**
         * EXIF may contain image description, although with undefined encoding.
         * Use IPTC for other annotation fields, and XMP for unicode support.
         */
        public void handleCommentTags(Directory directory, Metadata metadata) {
            if (metadata.get(TikaCoreProperties.DESCRIPTION) == null &&
                    directory.containsTag(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)) {
                metadata.set(TikaCoreProperties.DESCRIPTION, 
                        directory.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION));
            }
        }
        /**
         * Maps common TIFF and EXIF tags onto the Tika
         *  TIFF image metadata namespace.
         */       
        public void handlePhotoTags(Directory directory, Metadata metadata) {
            if(directory.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
               Object exposure = directory.getObject(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
               if(exposure instanceof Rational) {
                  metadata.set(Metadata.EXPOSURE_TIME, ((Rational)exposure).doubleValue());
               } else {
                  metadata.set(Metadata.EXPOSURE_TIME, directory.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
               }
            }
            
            if(directory.containsTag(ExifSubIFDDirectory.TAG_FLASH)) {
               String flash = directory.getDescription(ExifSubIFDDirectory.TAG_FLASH);
               if(flash.indexOf("Flash fired") > -1) {
                  metadata.set(Metadata.FLASH_FIRED, Boolean.TRUE.toString());
               }
               else if(flash.indexOf("Flash did not fire") > -1) {
                  metadata.set(Metadata.FLASH_FIRED, Boolean.FALSE.toString());
               }
               else {
                  metadata.set(Metadata.FLASH_FIRED, flash);
               }
            }

            if(directory.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
               Object fnumber = directory.getObject(ExifSubIFDDirectory.TAG_FNUMBER);
               if(fnumber instanceof Rational) {
                  metadata.set(Metadata.F_NUMBER, ((Rational)fnumber).doubleValue());
               } else {
                  metadata.set(Metadata.F_NUMBER, directory.getString(ExifSubIFDDirectory.TAG_FNUMBER));
               }
            }
            
            if(directory.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)) {
               Object length = directory.getObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
               if(length instanceof Rational) {
                  metadata.set(Metadata.FOCAL_LENGTH, ((Rational)length).doubleValue());
               } else {
                  metadata.set(Metadata.FOCAL_LENGTH, directory.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
               }
            }
            
            if(directory.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)) {
               metadata.set(Metadata.ISO_SPEED_RATINGS, directory.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
            }
          
            if(directory.containsTag(ExifIFD0Directory.TAG_MAKE)) {
               metadata.set(Metadata.EQUIPMENT_MAKE, directory.getString(ExifIFD0Directory.TAG_MAKE));
            }
            if(directory.containsTag(ExifIFD0Directory.TAG_MODEL)) {
               metadata.set(Metadata.EQUIPMENT_MODEL, directory.getString(ExifIFD0Directory.TAG_MODEL));
            }
          
            if(directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
               Object length = directory.getObject(ExifIFD0Directory.TAG_ORIENTATION);
               if(length instanceof Integer) {
                  metadata.set(Metadata.ORIENTATION, Integer.toString( ((Integer)length).intValue() ));
               } else {
                  metadata.set(Metadata.ORIENTATION, directory.getString(ExifIFD0Directory.TAG_ORIENTATION));
               }
            }
            
            if(directory.containsTag(ExifIFD0Directory.TAG_SOFTWARE)) {
               metadata.set(Metadata.SOFTWARE, directory.getString(ExifIFD0Directory.TAG_SOFTWARE));
            }
            
            if(directory.containsTag(ExifIFD0Directory.TAG_X_RESOLUTION)) {
               Object resolution = directory.getObject(ExifIFD0Directory.TAG_X_RESOLUTION);
               if(resolution instanceof Rational) {
                  metadata.set(Metadata.RESOLUTION_HORIZONTAL, ((Rational)resolution).doubleValue());
               } else {
                  metadata.set(Metadata.RESOLUTION_HORIZONTAL, directory.getString(ExifIFD0Directory.TAG_X_RESOLUTION));
               }
            }
            if(directory.containsTag(ExifIFD0Directory.TAG_Y_RESOLUTION)) {
               Object resolution = directory.getObject(ExifIFD0Directory.TAG_Y_RESOLUTION);
               if(resolution instanceof Rational) {
                  metadata.set(Metadata.RESOLUTION_VERTICAL, ((Rational)resolution).doubleValue());
               } else {
                  metadata.set(Metadata.RESOLUTION_VERTICAL, directory.getString(ExifIFD0Directory.TAG_Y_RESOLUTION));
               }
            }
            if(directory.containsTag(ExifIFD0Directory.TAG_RESOLUTION_UNIT)) {
               metadata.set(Metadata.RESOLUTION_UNIT, directory.getDescription(ExifIFD0Directory.TAG_RESOLUTION_UNIT));
            }
            if(directory.containsTag(ExifThumbnailDirectory.TAG_THUMBNAIL_IMAGE_WIDTH)) {
                metadata.set(Metadata.IMAGE_WIDTH, directory.getDescription(ExifThumbnailDirectory.TAG_THUMBNAIL_IMAGE_WIDTH));
            }
            if(directory.containsTag(ExifThumbnailDirectory.TAG_THUMBNAIL_IMAGE_HEIGHT)) {
                metadata.set(Metadata.IMAGE_LENGTH, directory.getDescription(ExifThumbnailDirectory.TAG_THUMBNAIL_IMAGE_HEIGHT));
            }
        }
        /**
         * Maps exif dates to metadata fields.
         */
        public void handleDateTags(Directory directory, Metadata metadata)
                throws MetadataException {
            // Date/Time Original overrides value from ExifDirectory.TAG_DATETIME
            Date original = null;
            if (directory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                original = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                // Unless we have GPS time we don't know the time zone so date must be set
                // as ISO 8601 datetime without timezone suffix (no Z or +/-)
                if (original != null) {
                    String datetimeNoTimeZone = DATE_UNSPECIFIED_TZ.format(original); // Same time zone as Metadata Extractor uses
                    metadata.set(TikaCoreProperties.CREATED, datetimeNoTimeZone);
                    metadata.set(Metadata.ORIGINAL_DATE, datetimeNoTimeZone);
                }
            }
            if (directory.containsTag(ExifIFD0Directory.TAG_DATETIME)) {
                Date datetime = directory.getDate(ExifIFD0Directory.TAG_DATETIME);
                String datetimeNoTimeZone = DATE_UNSPECIFIED_TZ.format(datetime);
                metadata.set(TikaCoreProperties.MODIFIED, datetimeNoTimeZone);
                // If Date/Time Original does not exist this might be creation date
                if (metadata.get(TikaCoreProperties.CREATED) == null) {
                    metadata.set(TikaCoreProperties.CREATED, datetimeNoTimeZone);
                }
            }
        }
    }
    
    /**
     * Reads image comments, originally TIKA-472.
     * Metadata Extractor does not read XMP so we need to use the values from Iptc or EXIF
     */
    static class IptcHandler implements DirectoryHandler {
        public boolean supports(Class<? extends Directory> directoryType) {
            return directoryType == IptcDirectory.class;
        }
        public void handle(Directory directory, Metadata metadata)
                throws MetadataException {
            if (directory.containsTag(IptcDirectory.TAG_KEYWORDS)) {
                String[] keywords = directory.getStringArray(IptcDirectory.TAG_KEYWORDS);
                for (String k : keywords) {
                    metadata.add(TikaCoreProperties.KEYWORDS, k);
                }
            }
            if (directory.containsTag(IptcDirectory.TAG_HEADLINE)) {
                metadata.set(TikaCoreProperties.TITLE, directory.getString(IptcDirectory.TAG_HEADLINE));
            } else if (directory.containsTag(IptcDirectory.TAG_OBJECT_NAME)) {
                metadata.set(TikaCoreProperties.TITLE, directory.getString(IptcDirectory.TAG_OBJECT_NAME));
            }
            if (directory.containsTag(IptcDirectory.TAG_BY_LINE)) {
                metadata.set(TikaCoreProperties.CREATOR, directory.getString(IptcDirectory.TAG_BY_LINE));
                metadata.set(IPTC.CREATOR, directory.getString(IptcDirectory.TAG_BY_LINE));
            }
            if (directory.containsTag(IptcDirectory.TAG_CAPTION)) {
                metadata.set(TikaCoreProperties.DESCRIPTION,
                        // Looks like metadata extractor returns IPTC newlines as a single carriage return,
                        // but the exiv2 command does not so we change to line feed here because that is less surprising to users                        
                        directory.getString(IptcDirectory.TAG_CAPTION).replaceAll("\r\n?", "\n"));
            }
        }
    }

    /**
     * Maps EXIF Geo Tags onto the Tika Geo metadata namespace.
     */
    static class GeotagHandler implements DirectoryHandler {
        public boolean supports(Class<? extends Directory> directoryType) {
            return directoryType == GpsDirectory.class;
        }
        public void handle(Directory directory, Metadata metadata) throws MetadataException {
            GeoLocation geoLocation = ((GpsDirectory) directory).getGeoLocation();
            if (geoLocation != null) {
                DecimalFormat geoDecimalFormat = new DecimalFormat(GEO_DECIMAL_FORMAT_STRING,
                        new DecimalFormatSymbols(Locale.ENGLISH));
                metadata.set(TikaCoreProperties.LATITUDE, geoDecimalFormat.format(new Double(geoLocation.getLatitude())));
                metadata.set(TikaCoreProperties.LONGITUDE, geoDecimalFormat.format(new Double(geoLocation.getLongitude())));
            }
        }
    }

}
