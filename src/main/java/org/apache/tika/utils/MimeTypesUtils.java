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
package org.apache.tika.utils;

import java.io.File;
import java.net.URL;

/**
 * Detect the MIME type of a document from file name given as
 * a String, a File, or a URL.
 */
public class MimeTypesUtils {

    /**
     * Returns the MIME type as specified by the ending of the name.
     *
     * @param name the resource name, e.g. "filename.pdf"
     * @return the MIME type, e.g. "application/pdf"
     */
    public static String getMimeType(String name) {
        // FIXME: See TIKA-8
        name = name.toLowerCase();
        if (name.endsWith(".txt")) {
            return "text/plain";
        } else if (name.endsWith(".pdf")) {
            return "application/pdf";
        } else if (name.endsWith(".htm")) {
            return "text/html";
        } else if (name.endsWith(".html")) {
            return "text/html";
        } else if (name.endsWith(".xhtml")) {
            return "application/xhtml+xml";
        } else if (name.endsWith(".xml")) {
            return "application/xml";
        } else if (name.endsWith(".doc")) {
            return "application/msword";
        } else if (name.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (name.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (name.endsWith(".zip")) {
            return "application/zip";
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * Returns the MIME type as specified by the ending of the file's name.
     *
     * @param file the file to test, e.g. new File("filename.pdf")
     * @return the MIME type, e.g. "application/pdf"
     */
    public static String getMimeType(File file) {
        return getMimeType(file.getName());
    }


    /**
     * Returns the MIME type as specified by the ending of the URL's file name.
     *
     * @param url the url to test, e.g. new URL("http://mydomain.com/filename.pdf")
     * @return the MIME type, e.g. "application/pdf"
     */
    public static String getMimeType(URL url) {
        return getMimeType(url.getPath());
    }

}
