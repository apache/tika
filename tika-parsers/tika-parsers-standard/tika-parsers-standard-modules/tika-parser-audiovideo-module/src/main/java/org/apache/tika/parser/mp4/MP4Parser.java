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
package org.apache.tika.parser.mp4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.drew.imaging.mp4.Mp4Reader;
import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4SoundDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * Parser for the MP4 media container format, as well as the older
 * QuickTime format that MP4 is based on.
 * <p>
 * This uses the MP4Parser project from http://code.google.com/p/mp4parser/
 * to do the underlying parsing
 */
public class MP4Parser extends AbstractParser {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 84011216792285L;
    /**
     * TODO Replace this with a 2dp Duration Property Converter
     */
    private static final DecimalFormat DURATION_FORMAT =
            (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
    // Ensure this stays in Sync with the entries in tika-mimetypes.xml
    private static final Map<MediaType, List<String>> typesMap = new HashMap<>();
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(typesMap.keySet());

    static {
        DURATION_FORMAT.applyPattern("0.0#");
    }

    static {
        // All types should be 4 bytes long, space padded as needed
        typesMap.put(MediaType.audio("mp4"), Arrays.asList("M4A ", "M4B ", "F4A ", "F4B "));
        typesMap.put(MediaType.video("3gpp"),
                Arrays.asList("3ge6", "3ge7", "3gg6", "3gp1", "3gp2", "3gp3", "3gp4", "3gp5",
                        "3gp6", "3gs7"));
        typesMap.put(MediaType.video("3gpp2"), Arrays.asList("3g2a", "3g2b", "3g2c"));
        typesMap.put(MediaType.video("mp4"), Arrays.asList("mp41", "mp42"));
        typesMap.put(MediaType.video("x-m4v"), Arrays.asList("M4V ", "M4VH", "M4VP"));

        typesMap.put(MediaType.video("quicktime"), Collections.emptyList());
        typesMap.put(MediaType.application("mp4"), Collections.emptyList());
    }

    private ISO6709Extractor iso6709Extractor = new ISO6709Extractor();

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tstream = TikaInputStream.get(stream, tmp);

        try (InputStream is = Files.newInputStream(tstream.getPath())) {

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            com.drew.metadata.Metadata mp4Metadata = new com.drew.metadata.Metadata();
            Mp4BoxHandler boxHandler = new TikaMp4BoxHandler(mp4Metadata, metadata, xhtml);
            try {
                Mp4Reader.extract(is, boxHandler);
            } catch (RuntimeSAXException e) {
                throw (SAXException) e.getCause();
            }
            //TODO -- figure out how to get IOExceptions out of boxhandler. Mp4Reader
            //currently swallows IOExceptions.
            processMp4Directories(mp4Metadata.getDirectoriesOfType(Mp4Directory.class), metadata);
            xhtml.endDocument();
        } finally {
            tmp.dispose();
        }
    }

    private void processMp4Directories(Collection<Mp4Directory> mp4Directories, Metadata metadata) {
        for (Mp4Directory mp4Directory : mp4Directories) {
            if (mp4Directory instanceof Mp4SoundDirectory) {
                processMp4SoundDirectory((Mp4SoundDirectory) mp4Directory, metadata);
            } else if (mp4Directory instanceof Mp4VideoDirectory) {
                processMp4VideoDirectory((Mp4VideoDirectory) mp4Directory, metadata);
            } else {
                processActualMp4Directory(mp4Directory, metadata);
            }
        }
    }

    private void processMp4VideoDirectory(Mp4VideoDirectory mp4Directory, Metadata metadata) {
        //todo
    }

    private void processMp4SoundDirectory(Mp4SoundDirectory mp4SoundDirectory,
                                        Metadata metadata) {
        addInt(mp4SoundDirectory, metadata, Mp4SoundDirectory.TAG_AUDIO_SAMPLE_RATE,
                XMPDM.AUDIO_SAMPLE_RATE);

        try {
            int numChannels = mp4SoundDirectory.getInt(Mp4SoundDirectory.TAG_NUMBER_OF_CHANNELS);
            if (numChannels == 1) {
                metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "Mono");
            } else if (numChannels == 2) {
                metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "Stereo");
            } else {
                //??? log
            }
        } catch (MetadataException e) {
            //log
        }
    }

    private void addInt(Mp4Directory mp4Directory, Metadata metadata, int tag,
                        Property property) {
        try {
            int val = mp4Directory.getInt(tag);
            metadata.set(property, val);
        } catch (MetadataException e) {
            //log
        }
    }

    private void processActualMp4Directory(Mp4Directory mp4Directory, Metadata metadata) {
        addDate(mp4Directory, metadata, Mp4Directory.TAG_CREATION_TIME, TikaCoreProperties.CREATED);
        addDate(mp4Directory, metadata, Mp4Directory.TAG_MODIFICATION_TIME,
                TikaCoreProperties.MODIFIED);
        handleBrands(mp4Directory, metadata);
        handleDurationInSeconds(mp4Directory, metadata);

        addDouble(mp4Directory, metadata, Mp4Directory.TAG_LATITUDE, TikaCoreProperties.LATITUDE);
        addDouble(mp4Directory, metadata, Mp4Directory.TAG_LONGITUDE, TikaCoreProperties.LONGITUDE);

    }

    private void handleDurationInSeconds(Mp4Directory mp4Directory, Metadata metadata) {
        String durationInSeconds = mp4Directory.getString(Mp4Directory.TAG_DURATION_SECONDS);
        if (durationInSeconds == null) {
            return;
        }
        if (! durationInSeconds.contains("/")) {
            return;
        }
        String[] bits = durationInSeconds.split("/");
        if (bits.length != 2) {
            return;
        }
        double durationSeconds;
        try {
            long numerator = Long.parseLong(bits[0]);
            long denominator = Long.parseLong(bits[1]);
            durationSeconds = (double)numerator/(double)denominator;
        } catch (NumberFormatException e) {
            //log
            return;
        }
        // Get the duration
        metadata.set(XMPDM.DURATION, DURATION_FORMAT.format(durationSeconds));
    }

    private void handleBrands(Mp4Directory mp4Directory, Metadata metadata) {


        String majorBrand = mp4Directory.getString(Mp4Directory.TAG_MAJOR_BRAND);
        // Identify the type based on the major brand
        Optional<MediaType> typeHolder = typesMap.entrySet().stream()
                .filter(e -> e.getValue().contains(majorBrand)).findFirst()
                .map(Map.Entry::getKey);

        if (!typeHolder.isPresent()) {
            String compatibleBrands =
                    mp4Directory.getString(Mp4Directory.TAG_COMPATIBLE_BRANDS);
            if (compatibleBrands != null) {
                // If no match for major brand, see if any of the compatible brands match
                typeHolder = typesMap.entrySet().stream().filter(e ->
                        e.getValue().stream().anyMatch(compatibleBrands::contains))
                        .findFirst().map(Map.Entry::getKey);
            }
        }
        MediaType type = typeHolder.orElse(MediaType.application("mp4"));
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
        if (type.getType().equals("audio") && ! StringUtils.isBlank(majorBrand)) {
            metadata.set(XMPDM.AUDIO_COMPRESSOR, majorBrand.trim());
        }

    }

    private void addDate(Mp4Directory mp4Directory, Metadata metadata, int tag,
                         Property property) {
        Date d = mp4Directory.getDate(tag);
        if (d == null) {
            return;
        }
        metadata.set(property, d);

    }

    private void addDouble(Directory mp4Directory, Metadata metadata, int tag,
                           Property property) {
        try {
            double val = mp4Directory.getDouble(tag);
            metadata.set(property, val);
        } catch (MetadataException e) {
            //log
            return;
        }

    }
}
