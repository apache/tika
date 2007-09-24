/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

// Tika imports
import org.apache.tika.utils.Configurable;
import org.apache.tika.utils.Configuration;
import org.apache.tika.metadata.TikaMimeKeys;

/**
 * 
 * Wrapper external interface around a {@link MimeTypes} repository.
 */
public class MimeUtils implements Configurable, TikaMimeKeys {

    /** My logger */
    private final static Logger LOG = Logger.getLogger(MimeUtils.class
            .getName());

    /** The key used to cache the mime repository in conf */
    private final static String KEY = MimeUtils.class.getName();

    /** My current configuration */
    private Configuration conf = null;

    /** A flag that tells if magic resolution must be performed */
    private boolean magic = true;

    /** The MimeTypes repository instance */
    private MimeTypes repository = null;

    /** Creates a new instance of MimeUtils */
    public MimeUtils(Configuration conf) {
        setConf(conf);
    }

    /***************************************************************************
     * ----------------------------- <implementation:Configurable> *
     * -----------------------------
     */

    public void setConf(Configuration conf) {
        this.conf = conf;
        this.magic = conf.getBoolean(MIME_TYPE_MAGIC, true);
        this.repository = (MimeTypes) conf.getObject(KEY);
        if (repository == null) {
            repository = load(conf.get(TIKA_MIME_FILE));
            conf.setObject(KEY, repository);
        }
    }

    public Configuration getConf() {
        return this.conf;
    }

    /***************************************************************************
     * ----------------------------- </implementation:Configurable> *
     * -----------------------------
     */

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

    private final MimeTypes load(String tikaMimeFile) {
        LOG.info("Loading [" + tikaMimeFile + "]");
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

}
