package org.apache.tika.example;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;

public class PhoneExtractingContentHandler extends ContentHandlerDecorator {
    protected final PhoneNumberUtil phoneUtil;
    private Metadata metadata;
    private static final String PHONE_NUMBERS = "phonenumbers";

    /**
     * Creates a decorator for the given SAX event handler.
     *
     * @param handler SAX event handler to be decorated
     */
    public PhoneExtractingContentHandler(ContentHandler handler, Metadata metadata) {
        super(handler);
        phoneUtil = PhoneNumberUtil.getInstance();
        this.metadata = metadata;
    }

    /**
     * Creates a decorator that by default forwards incoming SAX events to
     * a dummy content handler that simply ignores all the events. Subclasses
     * should use the {@link #setContentHandler(ContentHandler)} method to
     * switch to a more usable underlying content handler.
     */
    protected PhoneExtractingContentHandler() {
        this(new DefaultHandler(), new Metadata());
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        try {
            String text = new String(Arrays.copyOfRange(ch, start, start + length));
            for (PhoneNumberMatch match : phoneUtil.findNumbers(text, "US")) {
                metadata.add(PHONE_NUMBERS, match.number().toString());
            }
            super.characters(ch, start, length);
        } catch (SAXException e) {
            handleException(e);
        }
    }
}
