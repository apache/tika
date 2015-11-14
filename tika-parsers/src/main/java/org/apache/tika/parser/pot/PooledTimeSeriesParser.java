package org.apache.tika.parser.pot;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jlibs.core.lang.JavaProcessBuilder;
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
    	JavaProcessBuilder jvm = new JavaProcessBuilder();
    	//jvm.javaHome(new File("/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home")); // configure java home
    	jvm.javaHome(new File(System.getProperty("java.home"))); // configure java home
		jvm.workingDir(new File(config.getPooledTimeSeriesPath())); // to configure working directory to configure various attributes:

		// to configure heap and vmtype
		jvm.initialHeap(512); // or jvm.initialHeap("512m");
		jvm.maxHeap(1024); // or jvm.maxHeap("1024m");

		jvm.jvmArg("-jar");
		jvm.jvmArg(getPooledTimeSeriesProg());
		
		jvm.libraryPath(config.getOpenCVPath());
		
		jvm.jvmArg("-f");
		jvm.jvmArg(input.getPath());
		
		
		Process process;
		try {
			String command[] = jvm.command();
			process = jvm.launch(System.out, System.err);
			process.waitFor();

	        process.getOutputStream().close();
	        InputStream out = process.getInputStream();
	        InputStream err = process.getErrorStream();

			logStream("POT MSG", out, input);
			logStream("POT ERROR", err, input);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

	/**
     * Starts a thread that reads the contents of the standard output or error
     * stream of the given process to not block the process. The stream is closed
     * once fully processed.
     */
    private void logStream(final String logType, final InputStream stream, final File file) {
        new Thread() {
            public void run() {
                Reader reader = new InputStreamReader(stream, UTF_8);
                StringBuilder out = new StringBuilder();
                char[] buffer = new char[1024];
                try {
                    for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
                        out.append(buffer, 0, n);
                } catch (IOException e) {

                } finally {
                    IOUtils.closeQuietly(stream);
                }

                String msg = out.toString();
                LogFactory.getLog(PooledTimeSeriesParser.class).debug(msg);
            }
        }.start();
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

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
        String line = reader.readLine();
        String[] firstLine = line.split(" ");
        String frames = firstLine[0];
        String vecSize = firstLine[1];
        
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "numRows", "CDATA", frames);
        attributes.addAttribute("", "", "numCols", "CDATA", vecSize);
        
        xhtml.startDocument();
        xhtml.startElement("div", attributes);
        
        xhtml.startElement("ol");
        while ((line = reader.readLine()) != null) {
        	xhtml.startElement("ol");
        	for (String val : line.split(" ")) {
        		xhtml.startElement("li");
        		xhtml.characters(val);
        		xhtml.endElement("li");
        	}
        	xhtml.endElement("ol");
        }
        xhtml.endElement("ol");
        
        xhtml.endDocument();
    }

    static String getPooledTimeSeriesProg() {
        // TODO: Seperate out target/ folder from excutable jar
        return "target/pooled-time-series-1.0-SNAPSHOT-jar-with-dependencies.jar";
    }

}
