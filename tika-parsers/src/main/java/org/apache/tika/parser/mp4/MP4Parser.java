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

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.FileTypeBox;
import com.coremedia.iso.boxes.MetaBox;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.coremedia.iso.boxes.SampleDescriptionBox;
import com.coremedia.iso.boxes.SampleTableBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.coremedia.iso.boxes.UserDataBox;
import com.coremedia.iso.boxes.apple.AppleItemListBox;
import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry;
import com.googlecode.mp4parser.boxes.apple.AppleAlbumBox;
import com.googlecode.mp4parser.boxes.apple.AppleArtistBox;
import com.googlecode.mp4parser.boxes.apple.AppleArtist2Box;
import com.googlecode.mp4parser.boxes.apple.AppleCommentBox;
import com.googlecode.mp4parser.boxes.apple.AppleCompilationBox;
import com.googlecode.mp4parser.boxes.apple.AppleDiskNumberBox;
import com.googlecode.mp4parser.boxes.apple.AppleEncoderBox;
import com.googlecode.mp4parser.boxes.apple.AppleGenreBox;
import com.googlecode.mp4parser.boxes.apple.AppleNameBox;
import com.googlecode.mp4parser.boxes.apple.AppleRecordingYear2Box;
import com.googlecode.mp4parser.boxes.apple.AppleTrackAuthorBox;
import com.googlecode.mp4parser.boxes.apple.AppleTrackNumberBox;
import com.googlecode.mp4parser.boxes.apple.Utf8AppleDataBox;

/**
 * Parser for the MP4 media container format, as well as the older
 *  QuickTime format that MP4 is based on.
 * 
 * This uses the MP4Parser project from http://code.google.com/p/mp4parser/
 *  to do the underlying parsing
 */
public class MP4Parser extends AbstractParser {
    /** Serial version UID */
    private static final long serialVersionUID = 84011216792285L;
    /** TODO Replace this with a 2dp Duration Property Converter */
    private static final DecimalFormat DURATION_FORMAT = 
            (DecimalFormat)NumberFormat.getNumberInstance(Locale.ROOT); 
    static {
        DURATION_FORMAT.applyPattern("0.0#");
    }
    
    // Ensure this stays in Sync with the entries in tika-mimetypes.xml
    private static final Map<MediaType,List<String>> typesMap = new HashMap<MediaType, List<String>>();
    static {
       // All types should be 4 bytes long, space padded as needed
       typesMap.put(MediaType.audio("mp4"), Arrays.asList(
             "M4A ", "M4B ", "F4A ", "F4B "));
       typesMap.put(MediaType.video("3gpp"), Arrays.asList(
             "3ge6", "3ge7", "3gg6", "3gp1", "3gp2", "3gp3", "3gp4", "3gp5", "3gp6", "3gs7"));
       typesMap.put(MediaType.video("3gpp2"), Arrays.asList(
             "3g2a", "3g2b", "3g2c"));
       typesMap.put(MediaType.video("mp4"), Arrays.asList(
             "mp41", "mp42"));
       typesMap.put(MediaType.video("x-m4v"), Arrays.asList(
             "M4V ", "M4VH", "M4VP"));
       
       typesMap.put(MediaType.video("quicktime"), Collections.<String>emptyList());
       typesMap.put(MediaType.application("mp4"), Collections.<String>emptyList());
    }

    private static final Set<MediaType> SUPPORTED_TYPES =
       Collections.unmodifiableSet(typesMap.keySet());

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }


    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        IsoFile isoFile;
        
        // The MP4Parser library accepts either a File, or a byte array
        // As MP4 video files are typically large, always use a file to
        //  avoid OOMs that may occur with in-memory buffering
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tstream = TikaInputStream.get(stream, tmp);
        try {
            isoFile = new IsoFile(new DirectFileReadDataSource(tstream.getFile()));
            tmp.addResource(isoFile);

            // Grab the file type box
            FileTypeBox fileType = getOrNull(isoFile, FileTypeBox.class);
            if (fileType != null) {
               // Identify the type
               MediaType type = MediaType.application("mp4");
               for (MediaType t : typesMap.keySet()) {
                  if (typesMap.get(t).contains(fileType.getMajorBrand())) {
                     type = t;
                     break;
                  }
               }
               metadata.set(Metadata.CONTENT_TYPE, type.toString());

               if (type.getType().equals("audio")) {
                  metadata.set(XMPDM.AUDIO_COMPRESSOR, fileType.getMajorBrand().trim());
               }
            } else {
               // Some older QuickTime files lack the FileType
               metadata.set(Metadata.CONTENT_TYPE, "video/quicktime");
            }


            // Get the main MOOV box
            MovieBox moov = getOrNull(isoFile, MovieBox.class);
            if (moov == null) {
               // Bail out
               return;
            }


            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();


            // Pull out some information from the header box
            MovieHeaderBox mHeader = getOrNull(moov, MovieHeaderBox.class);
            if (mHeader != null) {
               // Get the creation and modification dates
               metadata.set(Metadata.CREATION_DATE, mHeader.getCreationTime());
               metadata.set(TikaCoreProperties.MODIFIED, mHeader.getModificationTime());

               // Get the duration
               double durationSeconds = ((double)mHeader.getDuration()) / mHeader.getTimescale();
               metadata.set(XMPDM.DURATION, DURATION_FORMAT.format(durationSeconds));

               // The timescale is normally the sampling rate
               metadata.set(XMPDM.AUDIO_SAMPLE_RATE, (int)mHeader.getTimescale());
            }


            // Get some more information from the track header
            // TODO Decide how to handle multiple tracks
            List<TrackBox> tb = moov.getBoxes(TrackBox.class);
            if (tb.size() > 0) {
               TrackBox track = tb.get(0);

               TrackHeaderBox header = track.getTrackHeaderBox();
               // Get the creation and modification dates
               metadata.set(TikaCoreProperties.CREATED, header.getCreationTime());
               metadata.set(TikaCoreProperties.MODIFIED, header.getModificationTime());

               // Get the video with and height
               metadata.set(Metadata.IMAGE_WIDTH,  (int)header.getWidth());
               metadata.set(Metadata.IMAGE_LENGTH, (int)header.getHeight());

               // Get the sample information
               SampleTableBox samples = track.getSampleTableBox();
               SampleDescriptionBox sampleDesc = samples.getSampleDescriptionBox();
               if (sampleDesc != null) {
                  // Look for the first Audio Sample, if present
                  AudioSampleEntry sample = getOrNull(sampleDesc, AudioSampleEntry.class);
                  if (sample != null) {
                     XMPDM.ChannelTypePropertyConverter.convertAndSet(metadata, sample.getChannelCount());
                     //metadata.set(XMPDM.AUDIO_SAMPLE_TYPE, sample.getSampleSize());    // TODO Num -> Type mapping
                     metadata.set(XMPDM.AUDIO_SAMPLE_RATE, (int)sample.getSampleRate());
                     //metadata.set(XMPDM.AUDIO_, sample.getSamplesPerPacket());
                     //metadata.set(XMPDM.AUDIO_, sample.getBytesPerSample());
                  }
               }
            }

            // Get metadata from the User Data Box
            UserDataBox userData = getOrNull(moov, UserDataBox.class);
            if (userData != null) {
               MetaBox meta = getOrNull(userData, MetaBox.class);

               // Check for iTunes Metadata
               // See http://atomicparsley.sourceforge.net/mpeg-4files.html and
               //  http://code.google.com/p/mp4v2/wiki/iTunesMetadata for more on these
               AppleItemListBox apple = getOrNull(meta, AppleItemListBox.class);
               if (apple != null) {
                  // Title
                  AppleNameBox title = getOrNull(apple, AppleNameBox.class);
                  addMetadata(TikaCoreProperties.TITLE, metadata, title);

                  // Artist
                  AppleArtistBox artist = getOrNull(apple, AppleArtistBox.class);
                  addMetadata(TikaCoreProperties.CREATOR, metadata, artist);
                  addMetadata(XMPDM.ARTIST, metadata, artist);

                  // Album Artist
                  AppleArtist2Box artist2 = getOrNull(apple, AppleArtist2Box.class);
                  addMetadata(XMPDM.ALBUM_ARTIST, metadata, artist2);

                  // Album
                  AppleAlbumBox album = getOrNull(apple, AppleAlbumBox.class);
                  addMetadata(XMPDM.ALBUM, metadata, album);

                  // Composer
                  AppleTrackAuthorBox composer = getOrNull(apple, AppleTrackAuthorBox.class);
                  addMetadata(XMPDM.COMPOSER, metadata, composer);

                  // Genre
                  AppleGenreBox genre = getOrNull(apple, AppleGenreBox.class);
                  addMetadata(XMPDM.GENRE, metadata, genre);

                  // Year
                  AppleRecordingYear2Box year = getOrNull(apple, AppleRecordingYear2Box.class);
                  if (year != null) {
                      metadata.set(XMPDM.RELEASE_DATE, year.getValue());
                  }

                  // Track number
                  AppleTrackNumberBox trackNum = getOrNull(apple, AppleTrackNumberBox.class);
                  if (trackNum != null) {
                     metadata.set(XMPDM.TRACK_NUMBER, trackNum.getA());
                     //metadata.set(XMPDM.NUMBER_OF_TRACKS, trackNum.getB()); // TODO
                  }

                  // Disc number
                  AppleDiskNumberBox discNum = getOrNull(apple, AppleDiskNumberBox.class);
                  if (discNum != null) {
                     metadata.set(XMPDM.DISC_NUMBER, discNum.getA());
                  }

                  // Compilation
                  AppleCompilationBox compilation = getOrNull(apple, AppleCompilationBox.class);
                  if (compilation != null) {
                      metadata.set(XMPDM.COMPILATION, (int)compilation.getValue());
                  }

                  // Comment
                  AppleCommentBox comment = getOrNull(apple, AppleCommentBox.class);
                  addMetadata(XMPDM.LOG_COMMENT, metadata, comment);

                  // Encoder
                  AppleEncoderBox encoder = getOrNull(apple, AppleEncoderBox.class);
                  if (encoder != null) {
                      metadata.set(XMP.CREATOR_TOOL, encoder.getValue());
                  }


                  // As text
                  for (Box box : apple.getBoxes()) {
                     if (box instanceof Utf8AppleDataBox) {
                        xhtml.element("p", ((Utf8AppleDataBox)box).getValue());
                     }
                  }
               }

               // TODO Check for other kinds too
            }

            // All done
            xhtml.endDocument();

        } finally {
            tmp.dispose();
        }

    }
    
    private static void addMetadata(String key, Metadata m, Utf8AppleDataBox metadata) {
       if (metadata != null) {
          m.add(key, metadata.getValue());
       }
    }
    private static void addMetadata(Property prop, Metadata m, Utf8AppleDataBox metadata) {
       if (metadata != null) {
          m.set(prop, metadata.getValue());
       }
    }
    
    private static <T extends Box> T getOrNull(Container box, Class<T> clazz) {
       if (box == null) return null;

       List<T> boxes = box.getBoxes(clazz);
       if (boxes.size() == 0) {
          return null;
       }
       return boxes.get(0);
    }
}
