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
package org.apache.tika.parser.multiple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.NonDetectingEncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Inspired by TIKA-1443 and https://wiki.apache.org/tika/CompositeParserDiscussion
 *  this tries several different text encodings, then does the real
 *  text parsing based on which is "best".
 *  
 * The logic for "best" needs a lot of work!
 * 
 * This is not recommended for actual production use... It is mostly to
 *  prove that the {@link AbstractMultipleParser} environment is
 *  sufficient to support this use-case
 *
 * @deprecated Currently not suitable for real use, more a demo / prototype!
 */
public class PickBestTextEncodingParser extends AbstractMultipleParser {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 730345169223211807L;
    
    /**
     * Which charsets we should try
     */
    private String[] charsetsToTry;
    
    /**
     * What charset we felt was best
     * TODO Does this need to be thread-safe?
     */
    private String pickedCharset;
    /**
     * What text we got for each charset, so we can test for the best
     * TODO Does this need to be thread-safe?
     */
    private Map<String,String> charsetText;

    public PickBestTextEncodingParser(MediaTypeRegistry registry, String[] charsets) {
        // TODO Actually give 1 more TXTParser than we have charsets
        super(registry, MetadataPolicy.DISCARD_ALL, (Parser)null);
        this.charsetsToTry = charsets;
    }

    @Override
    protected void parserPrepare(Parser parser, Metadata metadata,
            ParseContext context) {
        super.parserPrepare(parser, metadata, context);
        
        // Specify which charset to try
        // TODO How to get the next one to try?
        Charset charset = Charset.forName(charsetsToTry[0]);
        context.set(EncodingDetector.class, 
                    new NonDetectingEncodingDetector(charset));
    }

    @Override
    protected boolean parserCompleted(Parser parser, Metadata metadata,
            ContentHandler handler, Exception exception) {
        // TODO How to get the current charset?
        // TODO Record the text
        // TODO If this was the last real charset, see which one is best
        
        // Always have the next parser tried
        return true;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
            Metadata originalMetadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // TODO Create our own ContentHandlerFactory
        // This will give a BodyContentHandler for each of the charset
        //  tests, then their real ContentHandler for the last one
        
        // TODO Have the parsing done with our ContentHandlerFactory instead
        super.parse(stream, handler, originalMetadata, context);
    }
}
