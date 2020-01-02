package org.apache.tika.parser.microsoft.onenote;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Because the specification for OneNote is not an open spec,
 * we will resort to approximating the result of Linux "strings"
 * program, which just dumps out the text to file.
 */
public class OneNoteLegacyDumpStrings {

    public static int MIN_STRING_LENGTH = 8;
    public static float ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO = 0.6f;

    OneNoteDirectFileResource oneNoteDirectFileResource;
    XHTMLContentHandler xhtml;

    public OneNoteLegacyDumpStrings(OneNoteDirectFileResource oneNoteDirectFileResource, XHTMLContentHandler xhtml) {
        this.oneNoteDirectFileResource = oneNoteDirectFileResource;
        this.xhtml = xhtml;
    }

    public void dump() throws TikaException, SAXException {
        dumpAscii();
        dumpUtf16LE();
    }

    private void dumpAscii() throws SAXException, TikaException {
        try {
            oneNoteDirectFileResource.position(0);

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            for (int b = oneNoteDirectFileResource.read(); b != -1; b = oneNoteDirectFileResource.read()) {
                if (b >= 0x20 && b < 0x7F) {
                    os.write(b);
                } else {
                    if (os.size() >= MIN_STRING_LENGTH) {
                        writeIfUseful(os);
                    }
                    os.reset();
                }
            }
            if (os.size() >= MIN_STRING_LENGTH) {
                writeIfUseful(os);
            }
        } catch (IOException e) {
            throw new TikaException("Could not extract text from legacy OneNote document", e);
        }
    }

    private void dumpUtf16LE() throws SAXException, TikaException {
        try {
            oneNoteDirectFileResource.position(0);

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            long sz = oneNoteDirectFileResource.size();

            for (long i = 0; i < sz - 1; ++i) {
                oneNoteDirectFileResource.position(i);

                int c1 = oneNoteDirectFileResource.read();
                int c2 = oneNoteDirectFileResource.read();

                if (c1 == 0x00 && c2 >= 0x20 && c2 < 0x7F) {
                    ++i;
                    os.write(c2);
                } else {
                    if (os.size() >= MIN_STRING_LENGTH) {
                        writeIfUseful(os);
                    }
                    os.reset();
                }
            }
            if (os.size() >= MIN_STRING_LENGTH) {
                writeIfUseful(os);
            }
        } catch (IOException e) {
            throw new TikaException("Could not extract text from legacy OneNote document", e);
        }
    }

    private void writeIfUseful(ByteArrayOutputStream os) throws SAXException {
        String str = new String(os.toByteArray(), StandardCharsets.US_ASCII);
        String [] spl = str.split(" ");
        if (spl.length > 1) {
            int numAlpha = 0;
            for (int i = 0; i < str.length(); ++i) {
                if (Character.isAlphabetic(str.charAt(i)) || Character.isWhitespace(i)) {
                    ++numAlpha;
                }
            }
            float ratioAlphaToOtherChars = (float) numAlpha / (float) str.length();
            if (ratioAlphaToOtherChars > ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO) {
                xhtml.characters(str);
                xhtml.characters("\n");
            }
        }
    }
}
