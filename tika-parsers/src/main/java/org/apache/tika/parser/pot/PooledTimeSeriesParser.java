package org.apache.tika.parser.pot;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Aditya on 9/15/15.
 */
public class PooledTimeSeriesParser extends AbstractParser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<MediaType>(Arrays.asList(new MediaType[]{
                    MediaType.video("avi"), MediaType.video("mp4")
                    // TODO: Add all supported video types
            })));


    // check if opencv is present
    private boolean hasOpenCV() {
        // TODO
        return false;
    }

    /**
     * Returns the set of media types supported by this parser when used
     * with the given parse context.
     *
     * @param context parse context
     * @return immutable set of media types
     * @since Apache Tika 0.7
     */
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {

        // TODO
        // Check PooledTImeSeries configs to see if OpenCV is installed
        // if yes, then advertise video mime types
        //  return SUPPORTED_TYPES;
        // else return Collections.empty()

        // FIXME: Resolve above TODO and remove this return statement
        return SUPPORTED_TYPES;
    }

    /**
     * Parses a document stream into a sequence of XHTML SAX events.
     * Fills in related document metadata in the given metadata object.
     * <p>
     * The given document stream is consumed but not closed by this method.
     * The responsibility to close the stream remains on the caller.
     * <p>
     * Information about the parsing context can be passed in the context
     * parameter. See the parser implementations for the kinds of context
     * information they expect.
     *
     * @param stream   the document stream (input)
     * @param handler  handler for the XHTML SAX events (output)
     * @param metadata document metadata (input and output)
     * @param context  parse context
     * @throws IOException   if the document stream could not be read
     * @throws SAXException  if the SAX events could not be processed
     * @throws TikaException if the document could not be parsed
     * @since Apache Tika 0.5
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        // TODO
        // Setup deps/pre-rqs

        // TODO: Check for PooledTImeSeries and OpenCV
        // if ( !hasOpenCV()) {
        //  return;
        // }
        //

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        TemporaryResources tmp = new TemporaryResources();
        File output = null;
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
            File input = tikaStream.getFile();

            //long size = tikaStream.getLength();

            output = tmp.createTemporaryFile();

            computePoT(input, output);

            doExtract(new FileInputStream(output), xhtml);

        }
        finally {
            tmp.dispose();
            if (output != null) {
                output.delete();
            }
        }
    }

    private void computePoT(File input, File output /* TODO PooledTimeSeriesConfig() */) throws IOException, TikaException {

        // TODO
        // formulate cmd for PoT.java
        // Question: Should we integrate the PoT code into Tika itself,
        // and handoff the inputstream to an instance of PoT?
        // (As opposed to invoking it form the cmd line)
    }


    /**
     * Reads the contents of the given stream and write it to the given XHTML
     * content handler. The stream is closed once fully processed.
     *
     * @param stream
     *          Stream where is the result of ocr
     * @param xhtml
     *          XHTML content handler
     * @throws SAXException
     *           if the XHTML SAX events could not be handled
     * @throws IOException
     *           if an input error occurred
     */
    private void doExtract(InputStream stream, XHTMLContentHandler xhtml) throws SAXException, IOException {
        // TODO
        xhtml.startDocument();

        xhtml.startElement("table");
        // TODO : extract feature vector from PoT output
        //      - i.e OF.txt and HOG.txt

        xhtml.endElement("table");
        xhtml.endDocument();
    }


}
