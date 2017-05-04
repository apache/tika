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
package org.apache.tika.eval.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;


public class MimeBuffer extends AbstractDBBuffer {

    private final PreparedStatement st;
    private final TikaConfig config;
    private final Connection connection;


    public MimeBuffer(Connection connection, TikaConfig config) throws SQLException {
        st = connection.prepareStatement("insert into " + AbstractProfiler.MIME_TABLE.getName() + "( " +
                Cols.MIME_ID.name() + ", " +
                Cols.MIME_STRING.name() + ", " +
                Cols.FILE_EXTENSION.name() + ") values (?,?,?)");
        this.config = config;
        this.connection = connection;
    }

    @Override
    public void write(int id, String value) throws RuntimeException {
        try {
            st.clearParameters();
            st.setInt(1, id);
            st.setString(2, value);
            try {
                String ext = MimeUtil.getExtension(value, config);
                if (ext == null || ext.length() == 0) {
                    st.setNull(3, Types.VARCHAR);
                } else {
                    st.setString(3, ext);
                }
            } catch (MimeTypeException e) {
                st.setNull(3, Types.VARCHAR);
            }
            st.execute();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws SQLException {
        st.close();
        connection.commit();
        connection.close();
    }

    private static class MimeUtil {
        //TODO: see if MimeType now works for these
        private static final String APPLICATION = "application";
        private static final String TEXT = "text";
        private static final String HTML = "html";
        private static final String XML = "xml";
        private static final String XHTML_XML = "xhtml+xml";
        private static final String CSS = "css";
        private static final String CSV = "csv";
        private static final String PLAIN = "plain";
        private static final String EMPTY_STRING = "";

        /**
         * Utility method to convert from a string value representing a content type
         * (e.g. "application/pdf") into the most common extension for that file type
         * (e.g. "pdf").
         * <p>
         * This will has special handling for texty filetypes whose MimeTypes
         * don't currently return anything for {@link org.apache.tika.mime.MimeType#getExtension};
         *
         * @param contentType string representing a content type, for example: "application/pdf"
         * @param config      config from which to get MimeRepository
         * @return extension or empty string
         * @throws org.apache.tika.mime.MimeTypeException thrown if MimeTypes can't parse the contentType
         */
        public static String getExtension(String contentType, TikaConfig config)
                throws MimeTypeException {
            MimeTypes types = config.getMimeRepository();
            MimeType mime = types.forName(contentType);
            return getExtension(mime);
        }

        public static String getExtension(MimeType mime) {

            String ext = mime.getExtension();
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }

            //special handling for text/html/xml
            if (ext.length() == 0) {
                ext = tryTextyTypes(mime.getType());
            }
            return ext;
        }

        private static String tryTextyTypes(MediaType mediaType) {

            String type = mediaType.getType();
            String subtype = mediaType.getSubtype();
            if (type.equals(TEXT)) {
                if (subtype.equals(HTML)) {
                    return HTML;
                } else if (subtype.equals(PLAIN)) {
                    return "txt";
                } else if (subtype.equals(CSS)) {
                    return CSS;
                } else if (subtype.equals(CSV)) {
                    return CSV;
                }
            } else if (type.equals(APPLICATION)) {
                if (subtype.equals(XML)) {
                    return XML;
                } else if (subtype.equals(XHTML_XML)) {
                    return "html";
                }
            }
            return EMPTY_STRING;
        }
    }

}
