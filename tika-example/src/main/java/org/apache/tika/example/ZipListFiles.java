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

package org.apache.tika.example;

import java.io.IOException;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Example code listing from Chapter 1. Lists a zip file's entries using JDK's
 * standard APIs.
 */
public class ZipListFiles {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (String file : args) {
                System.out.println("Files in " + file + " file:");
                listZipEntries(file);
            }
        }
    }

    public static void listZipEntries(String path) throws IOException {
        ZipFile zip = new ZipFile(path);
        for (ZipEntry entry : Collections.list(zip.entries())) {
            System.out.println(entry.getName());
        }
    }
}
