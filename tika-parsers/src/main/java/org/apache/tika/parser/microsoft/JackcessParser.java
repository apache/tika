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

package org.apache.tika.parser.microsoft;


import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import com.healthmarketscience.jackcess.CryptCodecProvider;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.DateTimeType;
import com.healthmarketscience.jackcess.util.LinkResolver;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser that handles Microsoft Access files via
 * <a href="http://jackcess.sourceforge.net/">Jackcess</a>
 * <p>
 * Many, many thanks to LexisNexis®/Health Market Science (HMS), Brian O'Neill,
 * and James Ahlborn for relicensing Jackcess to Apache v2.0!
 */
public class JackcessParser extends AbstractParser {

    public static final String SUMMARY_PROPERTY_PREFIX = "MDB_SUMMARY_PROP" + Metadata.NAMESPACE_PREFIX_DELIMITER;
    public static String MDB_PROPERTY_PREFIX = "MDB_PROP" + Metadata.NAMESPACE_PREFIX_DELIMITER;
    public static String USER_DEFINED_PROPERTY_PREFIX = "MDB_USER_PROP" + Metadata.NAMESPACE_PREFIX_DELIMITER;
    public static Property MDB_PW = Property.externalText("Password");
    private final static LinkResolver IGNORE_LINK_RESOLVER = new IgnoreLinkResolver();

    //TODO: figure out how to get this info
    // public static Property LINKED_DATABASES = Property.externalTextBag("LinkedDatabases");

    private static final long serialVersionUID = -752276948656079347L;

    private static final MediaType MEDIA_TYPE = MediaType.application("x-msaccess");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);

    private Locale locale = Locale.ROOT;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        TikaInputStream tis = TikaInputStream.get(stream);
        Database db = null;
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        String password = null;
        PasswordProvider passwordProvider = context.get(PasswordProvider.class);
        if (passwordProvider != null) {
            password = passwordProvider.getPassword(metadata);
        }
        try {
            if (password == null) {
                //do this to ensure encryption/wrong password exception vs. more generic
                //"need right codec" error message.
                db = new DatabaseBuilder(tis.getFile())
                        .setCodecProvider(new CryptCodecProvider())
                        .setReadOnly(true).open();
            } else {
                db = new DatabaseBuilder(tis.getFile())
                        .setCodecProvider(new CryptCodecProvider(password))
                        .setReadOnly(true).open();
            }
            db.setDateTimeType(DateTimeType.DATE);
            db.setLinkResolver(IGNORE_LINK_RESOLVER);//just in case
            JackcessExtractor ex = new JackcessExtractor(metadata, context, locale);
            ex.parse(db, xhtml);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Incorrect password")) {
                throw new EncryptedDocumentException(e);
            }
            throw e;
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (IOException e) {
                    //swallow = silent close
                }
            }
        }
        xhtml.endDocument();
    }

    private static final class IgnoreLinkResolver implements LinkResolver {
        //If links are resolved, Jackcess might try to open and process
        //any file on the current system that is specified as a linked db.
        //This could be a nasty security issue.
        @Override
        public Database resolveLinkedDatabase(Database database, String s) throws IOException {
            throw new AssertionError("DO NOT ALLOW RESOLVING OF LINKS!!!");
        }
    }
}
