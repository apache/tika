package org.apache.tika.parser.microsoft.ooxml.xwpf;

import java.util.HashSet;
import java.util.Set;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.utils.StringUtils;

/**
 * This is designed to extract features that are useful for forensics, e-discovery and digital preservation.
 * Specifically, the presence of: tracked changes, hidden text, comments and comment authors. Because several of these
 * features can be placed on run properties, which can be in lots of places, I found it simpler to scrape
 * the document xml
 */
public class XWPFFeatureExtractor {

    public void process(OPCPackage opcPackage) {
    }

    private static class FeatureHandler extends DefaultHandler {
        //see: https://www.ericwhite.com/blog/using-xml-dom-to-detect-tracked-revisions-in-an-open-xml-wordprocessingml-document/
        private static final Set<String> TRACK_CHANGES = Set.of("ins", "del", "moveFrom", "moveTo");
        private Set<String> authors = new HashSet<>();
        private boolean hasHidden = false;
        private boolean hasTrackChanges = false;
        private boolean hasComments = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            //we could check to ensure that the vanish element actually surround text
            //the current check could lead to false positives where <w:vanish/> is around a space or no text.
            if ("vanish".equals(localName)) {
                hasHidden = true;
            } else if (TRACK_CHANGES.contains(localName)) {
                String trackChangesAuthor = atts.getValue("author");
                if (!StringUtils.isBlank(trackChangesAuthor)) {
                    authors.add(trackChangesAuthor);
                }
                hasTrackChanges = true;
            } else if ("commentReference".equals(localName) || "commentRangeStart".equals(localName)) {
                hasComments = true;
            }
        }
    }
}
