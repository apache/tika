package org.apache.tika.sax;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class MetadataBodyContentHandler extends ToXMLContentHandler {

	boolean inBody = false;
	public MetadataBodyContentHandler(OutputStream stream, String encoding)
			throws UnsupportedEncodingException {
		super(stream, encoding);
	}


	@Override
	public void startElement(String uri, String localName, String name,
			Attributes atts) throws SAXException {
		if(!inBody) {
			super.startElement(uri, localName, name, atts);
		}
		if("body".equals(localName)) {
			inBody = true;
		}
        if ("img".equals(localName) && atts.getValue("alt") != null) {
            String nfo = "[image: " + atts.getValue("alt") + ']';

            characters(nfo.toCharArray(), 0, nfo.length());
        }

        if ("a".equals(localName) && atts.getValue("name") != null) {
            String nfo = "[bookmark: " + atts.getValue("name") + ']';

            characters(nfo.toCharArray(), 0, nfo.length());
        }
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if("body".equals(localName)) {
			inBody = false;
		}
		if(!inBody) {
			super.endElement(uri, localName, qName);
		}
	}
}
