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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by Aditya on 9/15/15.
 */
public class PooledTimeSeriesParser extends AbstractParser {
    private static final PooledTimeSeriesConfig DEFAULT_CONFIG = new PooledTimeSeriesConfig();
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
        PooledTimeSeriesConfig config = context.get(PooledTimeSeriesConfig.class, DEFAULT_CONFIG);


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

            computePoT(input, output, config);

            // Output is same as inputfilename with .of.txt as suffix
            // TODO: Repeast for .hog.txt
            output = new File(input.getAbsoluteFile() + ".of.txt");

            doExtract(new FileInputStream(output), xhtml);

        }
        finally {
            tmp.dispose();
            if (output != null) {
                output.delete();
            }
        }
    }

    private void computePoT(File input, File output, PooledTimeSeriesConfig config) throws IOException, TikaException {

        // TODO
        // formulate cmd for PoT.java
        // Question: Should we integrate the PoT code into Tika itself,
        // and handoff the inputstream to an instance of PoT?
        // (As opposed to invoking it form the cmd line)


        String[] cmd = {"java","-Djava.library.path ", config.getOpenCVPath(), " -jar "
                , config.getPooledTimeSeriesPath() + getPooledTimeSeriesProg(),
                input.getPath()
        };

        
        System.out.println(Arrays.asList(cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        final Process process = pb.start();

        process.getOutputStream().close();
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();

        //logStream("OCR MSG", out, input);
        //logStream("OCR ERROR", err, input);

        FutureTask<Integer> waitTask = new FutureTask<Integer>(new Callable<Integer>() {
            public Integer call() throws Exception {
                return process.waitFor();
            }
        });

        Thread waitThread = new Thread(waitTask);
        waitThread.start();

        try {
            waitTask.get(config.getTimeout(), TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            waitThread.interrupt();
            process.destroy();
            Thread.currentThread().interrupt();
            throw new TikaException("PooledTimeSeriesParser interrupted", e);

        } catch (ExecutionException e) {
            // should not be thrown

        } catch (TimeoutException e) {
            waitThread.interrupt();
            process.destroy();
            throw new TikaException("PooledTimeSeriesParser timeout", e);
        }

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

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
        String line = reader.readLine();

        xhtml.characters(line);

        xhtml.endElement("table");
        xhtml.endDocument();
    }

    static String getPooledTimeSeriesProg() {
        // TODO: Seperate out target/ folder from excutable jar
        return "target/pooled-time-series-1.0-SNAPSHOT-jar-with-dependencies.jar";
    }

}
