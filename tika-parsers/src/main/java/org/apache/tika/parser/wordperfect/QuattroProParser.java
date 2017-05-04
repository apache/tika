/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wordperfect;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * <p>Parser for Corel QuattroPro documents (part of Corel WordPerfect 
 * Office Suite).
 * Targets QPW v9 File Format 
 * but appears to be compatible with more recent versions too.</p>
 * @author Pascal Essiembre 
 */
public class QuattroProParser extends AbstractParser {

    private static final long serialVersionUID = 8941810225917012232L;

    private final static MediaType QP_BASE = MediaType.application("x-quattro-pro");
    public final static MediaType QP_9 = new MediaType(QP_BASE, "version", "9");
    public static final MediaType QP_7_8 = new MediaType(QP_BASE, "version", "7-8");


    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(QP_9);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, 
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        if (metadata.get(Metadata.CONTENT_TYPE) == null) {
            metadata.set(Metadata.CONTENT_TYPE, QP_9.toString());
        }
        
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        QPWTextExtractor extractor = new QPWTextExtractor();
        extractor.extract(stream, xhtml, metadata);

        xhtml.endDocument();
    }
}
