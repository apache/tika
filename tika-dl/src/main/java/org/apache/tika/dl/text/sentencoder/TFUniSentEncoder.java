package org.apache.tika.dl.text.sentencode;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class TFUniSentEncoder extends AbstractParser implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(TFUniSentEncoder.class);
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("encodings"));
    private static final String DEF_MODEL_URL = "https://raw.githubusercontent.com/USCDataScience/SentimentAnalysisParser/master/sentiment-models/en-netflix-sentiment.bin";
    private static final String BASE_MODEL_CACHE_DIR = System.getProperty("user.home") + File.separator + ".tika-dl" +
            File.separator + "models" + File.separator + "tf";
    private static final String MODEL_CACHE_DIR = BASE_MODEL_CACHE_DIR + File.separator + "universal-sentence-encoder-2";

    @Field
    private File modelDir = new File(MODEL_CACHE_DIR);

    @Field
    private String modelURL = DEF_MODEL_URL;

    //    private SentimentME classifier;

    private static synchronized File cachedDownload(File cacheDir, URI uri)
            throws IOException {

        if ("file".equals(uri.getScheme()) || uri.getScheme() == null) {
            return new File(uri);
        }
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        String[] parts = uri.toASCIIString().split("/");
        File cacheFile = new File(cacheDir, parts[parts.length - 1]);
        File successFlag = new File(cacheFile.getAbsolutePath() + ".success");

        if (cacheFile.exists() && successFlag.exists()) {
            LOG.info("Cache exist at {}. Not downloading it", cacheFile.getAbsolutePath());
        } else {
            if (successFlag.exists()) {
                successFlag.delete();
            }
            LOG.info("Cache doesn't exist. Going to make a copy");
            LOG.info("This might take a while! GET {}", uri);
            FileUtils.copyURLToFile(uri.toURL(), cacheFile, 5000, 60000);
            //restore the success flag again
            FileUtils.write(successFlag,
                    "CopiedAt:" + System.currentTimeMillis(),
                    Charset.defaultCharset());
        }
        return cacheFile;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            String modelPath = cachedDownload(modelDir, URI.create(modelURL)).getAbsolutePath();
            System.out.println(modelPath);
        } catch (Exception e) {
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler handler) throws TikaConfigException {
        //TODO -- what do we want to check?
    }

    /**
     * Returns the types supported
     *
     * @param context the parse context
     * @return the set of types supported
     */
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Performs the parse
     *
     * @param stream   the input
     * @param handler  the content handler
     * @param metadata the metadata passed
     * @param context  the context for the parser
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
//        if (classifier == null) {
//            LOG.warn(getClass().getSimpleName() + " is not configured properly.");
//            return;
//        }
//        String inputString = IOUtils.toString(stream, "UTF-8");
//        String sentiment = classifier.predict(inputString);
//        metadata.add("Sentiment", sentiment);
    }

}
