package org.apache.tika.fork;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Locale;

public class UpperCasingContentHandler extends DefaultHandler {
    StringBuilder sb = new StringBuilder();

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        String chars = new String(ch, start, length);
        sb.append(chars.toUpperCase(Locale.US));
    }

    @Override
    public String toString() {
        return sb.toString();
    }

}
