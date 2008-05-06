package org.apache.tika.parser.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ImageParser implements Parser {

    public void parse(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        String type = metadata.get(Metadata.CONTENT_TYPE);
        if (type != null) {
            Iterator<ImageReader> iterator =
                ImageIO.getImageReadersByMIMEType(type);
            if (iterator.hasNext()) {
                ImageReader reader = iterator.next();
                reader.setInput(ImageIO.createImageInputStream(
                        new CloseShieldInputStream(stream)));
                metadata.set("height", Integer.toString(reader.getHeight(0)));
                metadata.set("width", Integer.toString(reader.getWidth(0)));
                reader.dispose();
            }
        }
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, metadata);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

}
