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

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

import org.w3c.dom.Document;


/**
 * Creates instances of MimeTypes.
 */
public class MimeTypesFactory {

    /**
     * Creates an empty instance; same as calling new MimeTypes().
     *
     * @return an empty instance
     */
    public static MimeTypes create() {
        return new MimeTypes();
    }

    /**
     * Creates and returns a MimeTypes instance from the specified document.
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(Document document) throws MimeTypeException {
        MimeTypes mimeTypes = new MimeTypes();
        new MimeTypesReader(mimeTypes).read(document);
        return mimeTypes;
    }

    /**
     * Creates and returns a MimeTypes instance from the specified input stream.
     * Does not close the input stream.
     * @throws IOException if the stream can not be read
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(InputStream inputStream)
            throws IOException, MimeTypeException {
        MimeTypes mimeTypes = new MimeTypes();
        new MimeTypesReader(mimeTypes).read(inputStream);
        return mimeTypes;
    }

    /**
     * Creates and returns a MimeTypes instance from the resource
     * at the location specified by the URL.  Opens and closes the
     * InputStream from the URL.
     *
     * @throws IOException if the URL can not be accessed
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(URL url)
            throws IOException, MimeTypeException {
        InputStream stream = url.openStream();
        try {
            return create(stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Creates and returns a MimeTypes instance from the specified file path,
     * as interpreted by the class loader in getResource().
     *
     * @throws IOException if the file can not be accessed
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(String filePath)
            throws IOException, MimeTypeException {
        return create(MimeTypesReader.class.getResource(filePath));
    }
}
