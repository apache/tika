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

/**
 * Detect mime type from file
 * 
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */
public class MimeTypesUtils {

    public static String getMimeType(File file) {
        // FIXME: See TIKA-8
        String name = file.getName().toLowerCase();
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

}
