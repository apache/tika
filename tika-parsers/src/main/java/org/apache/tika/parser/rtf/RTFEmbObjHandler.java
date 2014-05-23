package org.apache.tika.parser.rtf; 
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 
import java.io.ByteArrayOutputStream; 
import java.io.IOException; 
import java.io.InputStream; 
import java.util.concurrent.atomic.AtomicInteger; 
import org.apache.tika.config.TikaConfig; 
import org.apache.tika.detect.Detector; 
import org.apache.tika.exception.TikaException; 
import org.apache.tika.extractor.EmbeddedDocumentExtractor; 
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor; 
import org.apache.tika.io.FilenameUtils; 
import org.apache.tika.io.TikaInputStream; 
import org.apache.tika.metadata.Metadata; 
import org.apache.tika.metadata.RTFMetadata; 
import org.apache.tika.mime.MediaType; 
import org.apache.tika.mime.MimeType; 
import org.apache.tika.mime.MimeTypeException; 
import org.apache.tika.mime.MimeTypes; 
import org.apache.tika.parser.ParseContext; 
import org.apache.tika.sax.EmbeddedContentHandler; 
import org.xml.sax.ContentHandler; 
import org.xml.sax.SAXException; 

/**
 * This class buffers data from embedded objects and pictures.
 *
 * <p/>
 *
 * When the parser has finished an object or picture and called
 * {@link #handleCompletedObject()}, this will write the object
 * to the {@link #handler}.
 *
 * <p/>
 *
 * This (in combination with TextExtractor) expects basically a flat parse.  It will pull out
 * all pict whether they are tied to objdata or are intended
 * to be standalone.
 *
 * <p/>
 * This tries to pull metadata around a pict that is encoded
 * with {sp {sn} {sv}} types of data.  This information
 * sometimes contains the name and even full file path of the original file.
 *
 */ class RTFEmbObjHandler {
    
    private static final String EMPTY_STRING = "";
    
    private enum EMB_STATE {
      PICT, //recording pict data
      OBJDATA, //recording objdata
      NADA
    };
    
    //high hex cached for writing hexpair chars (data)
    private int hi = -1;
    
    private int thumbCount = 0;
    //don't need atomic, do need mutable
    private AtomicInteger unknownFilenameCount = new AtomicInteger();
    
    private boolean inObject = false;
    
    private String sv = EMPTY_STRING;
    private String sn = EMPTY_STRING;
    
    private StringBuilder sb = new StringBuilder();
    
    private final ContentHandler handler;
    private Metadata metadata;
    private final ParseContext context;
    
    private final ByteArrayOutputStream os;
    private EMB_STATE state = EMB_STATE.NADA;
    
    protected RTFEmbObjHandler(ContentHandler handler, Metadata metadata, ParseContext context) {
        this.handler = handler;
        this.context = context;
        os = new ByteArrayOutputStream();
    }
    protected void startPict() {
        state = EMB_STATE.PICT;
        metadata = new Metadata();
    }
    
    protected void startObjData() {
        state = EMB_STATE.OBJDATA;
        metadata = new Metadata();
    }
    
    protected void startSN() {
        sb.setLength(0);
        sb.append(RTFMetadata.RTF_PICT_META_PREFIX);
    }
    
    protected void endSN() {
        sn = sb.toString();
    }
    
    protected void startSV() {
        sb.setLength(0);
    }
    
    protected void endSV() {
        sv = sb.toString();
    }
    
    //end metadata pair
    protected void endSP() {
        metadata.add(sn, sv);
    }
    
    protected void setInObject(boolean v) {
        inObject = v;
    }
    
    protected boolean getInObject() {
        return inObject;
    }
    
    protected void writeMetadataChar(char c) {
        sb.append(c);
    }
    
    protected void writeHexChar(int b) throws IOException, TikaException {
        //if not hexchar, ignore
        //white space is common
        if (TextExtractor.isHexChar(b)) {
            if (hi == -1) {
                hi = 16*TextExtractor.hexValue(b);
            } else {
                long sum = hi+TextExtractor.hexValue(b);
                if (sum > Integer.MAX_VALUE || sum < 0) {
                    throw new IOException("hex char to byte overflow");
                }
                
                os.write((int)sum);
                
                hi = -1;
            }
            return;
        }
        if (b == -1) {
            throw new TikaException("hit end of stream before finishing byte pair");
        }
    }
    
    
    protected void writeBytes(InputStream is, int len) throws IOException, TikaException {
        if (len < 0 || len > RTFParser.getMaxBytesForEmbeddedObject()) {
            throw new IOException("length of bytes to read out of bounds: " + len);
        }
        
        byte[] bytes = new byte[len];
        int bytesRead = is.read(bytes);
        if (bytesRead < len) {
            throw new TikaException("unexpected end of file: need " + len +
                   " bytes of binary data, found " + (len-bytesRead));
        }
        os.write(bytes);
    }
    
    /**
     * Call this when the objdata/pict has completed
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    protected void handleCompletedObject() throws IOException, SAXException, TikaException {
       EmbeddedDocumentExtractor embeddedExtractor = context.get(EmbeddedDocumentExtractor.class);
       
       if (embeddedExtractor == null) {
           embeddedExtractor = new ParsingEmbeddedDocumentExtractor(context);
       }
       
       byte[] bytes = os.toByteArray();
       if (state == EMB_STATE.OBJDATA) {
           RTFObjDataParser objParser = new RTFObjDataParser();
           try{
               byte[] objBytes = objParser.parse(bytes, metadata, unknownFilenameCount);
               extractObj(objBytes, handler, embeddedExtractor, metadata);
           } catch (IOException e) {
              //swallow.  If anything goes wrong, ignore.
           }
       } else if (state == EMB_STATE.PICT) {
           String filePath = metadata.get(RTFMetadata.RTF_PICT_META_PREFIX+"wzDescription");
           if (filePath != null && filePath.length() > 0){
               metadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, filePath);
               metadata.set(Metadata.RESOURCE_NAME_KEY, FilenameUtils.getName(filePath));
           }
           metadata.set(RTFMetadata.THUMBNAIL, Boolean.toString(inObject));
           extractObj(bytes, handler, embeddedExtractor, metadata);
           
       } else if (state == EMB_STATE.NADA) {
           //swallow...no start for pict or embed?!
       }
       reset();
    }
    
    private void extractObj(byte[] bytes, ContentHandler handler,
            EmbeddedDocumentExtractor embeddedExtractor, Metadata metadata)
                    throws SAXException, IOException, TikaException {
        
        if (bytes == null) {
            return;
        }
        
        metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(bytes.length));
        
        if (embeddedExtractor.shouldParseEmbedded(metadata)) {
            TikaInputStream stream = TikaInputStream.get(bytes);
            if (metadata.get(Metadata.RESOURCE_NAME_KEY) == null) {
                String extension = getExtension(stream, metadata);
                stream.reset();
                if (inObject && state == EMB_STATE.PICT) {
                    metadata.set(Metadata.RESOURCE_NAME_KEY, "thumbnail_"+thumbCount++ + extension);
                    metadata.set(RTFMetadata.THUMBNAIL, "true");
                } else {
                    metadata.set(Metadata.RESOURCE_NAME_KEY, "file_"+unknownFilenameCount.getAndIncrement() + 
extension);
                }
            }
            try {
                embeddedExtractor.parseEmbedded(
                        stream,
                        new EmbeddedContentHandler(handler),
                        metadata, false);
            } finally {
                stream.close();
            }
        }
    }
    
    private String getExtension(TikaInputStream is, Metadata metadata) {
        String cType = metadata.get(Metadata.CONTENT_TYPE);
        TikaConfig config = getConfig();
        if (cType == null) {
            Detector detector = config.getDetector();
            try {
                MediaType mediaType = detector.detect(is, metadata);
                MimeTypes types = config.getMimeRepository();
                MimeType mime = types.forName(mediaType.toString());
                metadata.set(Metadata.CONTENT_TYPE, mediaType.getSubtype());
                return mime.getExtension();
            } catch (IOException e) {
                //swallow
            } catch (MimeTypeException e) {
                
            }
        }
        return ".bin";
    }
    
    private TikaConfig getConfig() {
        TikaConfig config = context.get(TikaConfig.class);
        if (config == null) {
            config = TikaConfig.getDefaultConfig();
        }
        return config;
    }
    
    /**
     * reset state after each object.
     * Do not reset unknown file number.
     */
    protected void reset() {
        state = EMB_STATE.NADA;
        os.reset();
        metadata = new Metadata();
        hi = -1;
        sv = EMPTY_STRING;
        sn = EMPTY_STRING;
        sb.setLength(0);
    }
}
