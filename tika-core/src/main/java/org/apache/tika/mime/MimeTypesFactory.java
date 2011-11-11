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
package org.apache.tika.mime;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        mimeTypes.init();
        return mimeTypes;
    }

    /**
     * Creates and returns a MimeTypes instance from the specified input stream.
     * Does not close the input stream(s).
     * @throws IOException if the stream can not be read
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(InputStream... inputStreams)
            throws IOException, MimeTypeException {
        MimeTypes mimeTypes = new MimeTypes();
        MimeTypesReader reader = new MimeTypesReader(mimeTypes);
        for(InputStream inputStream : inputStreams) {
           reader.read(inputStream);
        }
        mimeTypes.init();
        return mimeTypes;
    }

    /** @see #create(InputStream...) */
    public static MimeTypes create(InputStream stream)
            throws IOException, MimeTypeException {
        return create(new InputStream[] { stream });
    }

    /**
     * Creates and returns a MimeTypes instance from the resource
     * at the location specified by the URL.  Opens and closes the
     * InputStream from the URL.
     * If multiple URLs are supplied, then they are loaded in turn. 
     *
     * @throws IOException if the URL can not be accessed
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(URL... urls)
            throws IOException, MimeTypeException {
        InputStream[] streams = new InputStream[urls.length];
        for(int i=0; i<streams.length; i++) {
           streams[i] = urls[i].openStream();
        }

        try {
            return create(streams);
        } finally {
            for(InputStream stream : streams) {
               stream.close();
            }
        }
    }

    /** @see #create(URL...) */
    public static MimeTypes create(URL url)
            throws IOException, MimeTypeException {
        return create(new URL[] { url });
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

    /**
     * Creates and returns a MimeTypes instance. The core mimetypes
     *  will be loaded from the specified file path, and any custom
     *  override mimetypes found will loaded afterwards.
     * The file paths will be interpreted by the class loader in 
     *  getResource().
     * 
     * @param coreFilePath The main MimeTypes file to load
     * @param extensionFilePath The name of extension MimeType files to load afterwards
     *
     * @throws IOException if the file can not be accessed
     * @throws MimeTypeException if the type configuration is invalid
     */
    public static MimeTypes create(String coreFilePath, String extensionFilePath)
            throws IOException, MimeTypeException {
        // This allows us to replicate class.getResource() when using
        //  the classloader directly
        String classPrefix = MimeTypesReader.class.getPackage().getName().replace('.', '/') + "/";
        ClassLoader cl = MimeTypesReader.class.getClassLoader();
       
        // Get the core URL, and all the extensions URLs
        URL coreURL = cl.getResource(classPrefix+coreFilePath);
        List<URL> extensionURLs = Collections.list(
              cl.getResources(classPrefix+extensionFilePath));

        // Swap that into an Array, and process
        List<URL> urls = new ArrayList<URL>();
        urls.add(coreURL);
        urls.addAll(extensionURLs);
        
        return create( urls.toArray(new URL[urls.size()]) );
    }
}
