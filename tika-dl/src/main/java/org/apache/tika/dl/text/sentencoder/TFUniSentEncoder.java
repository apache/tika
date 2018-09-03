/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.dl.text.sentencoder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensors;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This parser is powered by <a href="https://www.tensorflow.org/">TensorFlow</a>.
 * This implementation is pre configured to use <a href="https://arxiv.org/abs/1803.11175"> Universal Sentence Encoder </a>
 * This parser encodes a text into high dimensional vectors that can be used for text classification, semantic similarity,
 * clustering and other natural language tasks. The model is trained and optimized for greater-than-word length text,
 * such as sentences, phrases or short paragraphs. It is trained on a variety of data sources and a variety of tasks
 * with the aim of dynamically accommodating a wide variety of natural language understanding tasks.
 * The input is variable length English text and the output is a 512 dimensional vector.
 * <p>
 * Although this implementation is made to work out of the box without user attention,
 * for advances users who are interested in tweaking the settings, the following fields are configurable:
 * <ul>
 * <li>{@link #modelDir}</li>
 * <li>{@link #modelURL}</li>
 * </ul>
 * </p>
 *
 * @since Tika 2.0
 */

public class TFUniSentEncoder extends AbstractParser implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(TFUniSentEncoder.class);
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("encodings"));
    private static final String DEF_MODEL_URL = "https://www.dropbox.com/s/brls43rgzoqtee4/uni-sent-encoder.zip?dl=1";
    private static final String BASE_MODEL_CACHE_DIR = System.getProperty("user.home") + File.separator + ".tika-dl" +
            File.separator + "models" + File.separator + "tf";
    private static final String MODEL_CACHE_DIR = BASE_MODEL_CACHE_DIR + File.separator + "universal-sentence-encoder-2";
    private static final int BUFFER_SIZE = 4096;

    @Field
    private static String modelDir = MODEL_CACHE_DIR;

    @Field
    private static String modelURL = DEF_MODEL_URL;

    private static SavedModelBundle savedModel;

    private Session sess;

    /**
     * @param cacheDir directory to cache models
     * @param uri      URL to download models from
     * @return
     * @throws IOException for calling unzip method
     */
    private static synchronized File cachedDownload(File cacheDir, URI uri) throws IOException {

        if ("file".equals(uri.getScheme()) || uri.getScheme() == null) {
            return new File(uri);
        }
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        File zipFile = new File(cacheDir, "uni-sent-encoder.zip");

        File successFlag = new File(cacheDir, ".success");

        if (successFlag.exists()) {
            LOG.info("Cache exist at {}. Not downloading it", zipFile.getAbsolutePath());
        } else {
            if (successFlag.exists()) {
                successFlag.delete();
            }
            LOG.info("Cache doesn't exist. Going to make a copy");
            LOG.info("This might take a while! GET {}", uri);
            FileUtils.copyURLToFile(uri.toURL(), zipFile, 5000, 60000);
            LOG.info("Compressed model file downloaded");
            unzip(zipFile.getAbsolutePath(), modelDir);
            LOG.info("Extracted compressed model file");
            FileUtils.deleteQuietly(zipFile);
            FileUtils.write(successFlag, "CopiedAt:" + System.currentTimeMillis(), Charset.defaultCharset());
            LOG.info("Success flag restored");
        }
        return cacheDir;
    }

    /**
     * Extracts a zip file specified by the zipFilePath to a directory specified by
     * destDirectory (will be created if does not exists)
     *
     * @param zipFilePath   zip file path
     * @param destDirectory destination directory
     * @throws IOException when file does not exist
     */
    private static void unzip(String zipFilePath, String destDirectory) throws IOException {

        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdir();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    /**
     * Extracts a zip entry (file entry)
     *
     * @param zipIn    zip input stream
     * @param filePath file path
     * @throws IOException when file does not exist
     */
    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

        try {
            String modelPath = cachedDownload(new File(modelDir), URI.create(modelURL)).getAbsolutePath();
            savedModel = SavedModelBundle.load(modelPath, "serve");
            LOG.info("saved model successfully loaded into memory");
            sess = savedModel.session();
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
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        String inputString = IOUtils.toString(stream, "UTF-8");
        String[] m = inputString.split("\n");

        int textCount = m.length;
        int encodingLen = 512;

        byte[][] msgs = new byte[textCount][];

        for (int i = 0; i < textCount; i++) {
            msgs[i] = m[i].getBytes(UTF_8);
        }

        float[][] modelOutput = sess.runner()
                .feed("sentences", Tensors.create(msgs))
                .fetch("encodings:0")
                .run()
                .get(0)
                .copyTo(new float[textCount][encodingLen]);

        String encodings = Arrays.deepToString(modelOutput);
        metadata.add("UniversalSentenceEncodings", encodings);
    }

    public void dispose() {
        // Dispose model
        savedModel.close();
        LOG.info("Model successfully closed");
    }
}
