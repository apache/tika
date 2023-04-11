package org.apache.tika.parser.epub;

import org.apache.tika.metadata.Epub;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.DcXMLParser;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Use this to parse the .opf files
 */
public class OPFParser extends DcXMLParser {

    @Override
    protected ContentHandler getContentHandler(ContentHandler handler, Metadata metadata,
                                               ParseContext context) {
        //set default.  This will be overwritten if it is pre-paginated
        metadata.set(Epub.RENDITION_LAYOUT, "reflowable");
        return new TeeContentHandler(
                super.getContentHandler(handler, metadata, context),
                new OPFHandler(metadata)
                );
    }

    private class OPFHandler extends DefaultHandler {
        private final Metadata metadata;
        boolean inRenditionLayout = false;
        StringBuilder sb = new StringBuilder();
        public OPFHandler(Metadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            //check each item on spine for pre-paginated
            if ("itemref".equals(localName)) {
                String val = XMLReaderUtils.getAttrValue("properties", attributes);
                if (val != null && val.contains("rendition:layout-pre-paginated")) {
                    metadata.set(Epub.RENDITION_LAYOUT, "pre-paginated");
                }
            } else if ("meta".equals(localName)) {
                String prop = XMLReaderUtils.getAttrValue("property", attributes);
                if ("rendition:layout".equals(prop)) {
                    inRenditionLayout = true;
                }
            } else if ("package".equals(localName)) {
                String v = XMLReaderUtils.getAttrValue("version", attributes);
                if (!StringUtils.isBlank(v)) {
                    metadata.set(Epub.VERSION, v);
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (inRenditionLayout && "meta".equals(localName)) {
                String layout = sb.toString();
                if ("pre-paginated".equals(layout)) {
                    metadata.set(Epub.RENDITION_LAYOUT, "pre-paginated");
                }
                inRenditionLayout = false;
                sb.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inRenditionLayout) {
                sb.append(ch, start, length);
            }
        }
    }
}
