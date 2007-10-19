/**
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
package org.apache.tika.mime;

// JDK imports
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.tika.metadata.TikaMimeKeys;
import org.jdom.JDOMException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

// Tika imports
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;



/**
 * 
 * Wrapper external interface around a {@link MimeTypes} repository.
 */
public class MimeUtils implements TikaMimeKeys {

    /** My logger */
    private final static Logger LOG = Logger.getLogger(MimeUtils.class
            .getName());

    /** The MimeTypes repository instance */
    private MimeTypes repository = null;

    /** Creates a new instance of MimeUtils */
    public MimeUtils(String resPath) {
        if(repository == null){
            repository = load(resPath);
        }
    }


    /** Creates a new instance of MimeUtils */
    public MimeUtils() throws TikaException {
        try {
            repository = TikaConfig.getDefaultConfig().getMimeRepository();
        } catch (IOException e) {
            throw new TikaException(
                    "Unable to load default MIME type repository.", e);
        } catch (JDOMException e) {
            throw new TikaException(
                    "Unable to load default MIME type repository.", e);
        }
    }

    public final MimeTypes getRepository() {
        return repository;
    }

    public String getType(String typeName, String url, byte[] data) {
        MimeType type = null;
        try {
            typeName = MimeType.clean(typeName);
            type = typeName == null ? null : repository.forName(typeName);
        } catch (MimeTypeException mte) {
            // Seems to be a malformed mime type name...
        }

        if (typeName == null || type == null || !type.matches(url)) {
            // If no mime-type header, or cannot find a corresponding registered
            // mime-type, or the one found doesn't match the url pattern
            // it shouldbe, then guess a mime-type from the url pattern
            type = repository.getMimeType(url);
            typeName = type == null ? typeName : type.getName();
        }
        // if (typeName == null || type == null ||
        // (this.magic && type.hasMagic() && !type.matches(data))) {
        // If no mime-type already found, or the one found doesn't match
        // the magic bytes it should be, then, guess a mime-type from the
        // document content (magic bytes)
        type = repository.getMimeType(data);
        typeName = type == null ? typeName : type.getName();
        // }
        return typeName;
    }


    /**
     * Determines the MIME type of the resource pointed to by the specified URL.
     * Examines the file's header, and if it cannot determine the MIME type
     * from the header, guesses the MIME type from the URL extension
     * (e.g. "pdf).
     *
     * @param url
     * @return
     * @throws IOException
     */
    public String getType(URL url) throws IOException {
        InputStream stream = url.openStream();
        try {
            return getType(null, url.toString(), getHeader(stream));
        } finally {
            stream.close();
        }
    }

    private final MimeTypes load(String tikaMimeFile) {
        // The line below is disabled  until we can implement a
        // way of restricting this output by default. (see TIKA-82)
        //  LOG.info("Loading [" + tikaMimeFile + "]");
        Document document = getDocumentRoot(MimeUtils.class.getClassLoader()
                .getResourceAsStream(tikaMimeFile));

        MimeTypes types = new MimeTypes(document);
        return types;
    }

    private final Document getDocumentRoot(InputStream is) {
        // open up the XML file
        DocumentBuilderFactory factory = null;
        DocumentBuilder parser = null;
        Document document = null;
        InputSource inputSource = null;

        inputSource = new InputSource(is);

        try {
            factory = DocumentBuilderFactory.newInstance();
            parser = factory.newDocumentBuilder();
            document = parser.parse(inputSource);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Unable to parse xml stream"
                    + ": Reason is [" + e + "]");
            return null;
        }

        return document;
    }


    /**
     * Read the resource's header for use in determination of the MIME type.
     */
    private byte[] getHeader(InputStream stream) throws IOException {
        byte[] bytes = new byte[repository.getMinLength()];
        int totalRead = 0;
        int lastRead = stream.read(bytes);
        while (lastRead != -1) {
            totalRead += lastRead;
            if (totalRead == bytes.length) {
                return bytes;
            }
            lastRead = stream.read(bytes, totalRead, bytes.length - totalRead);
        }
        byte[] shorter = new byte[totalRead];
        System.arraycopy(bytes, 0, shorter, 0, totalRead);
        return shorter;
    }
}
