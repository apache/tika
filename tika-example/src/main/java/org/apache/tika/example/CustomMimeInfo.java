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

import java.net.URL;

import org.apache.tika.Tika;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;

public class CustomMimeInfo {
    public static String customMimeInfo() throws Exception {
        String path = "file:///path/to/prescription-type.xml";
        MimeTypes typeDatabase = MimeTypesFactory.create(new URL(path));
        Tika tika = new Tika(typeDatabase);
        return tika.detect("/path/to/prescription.xpd");
    }

    public static String customCompositeDetector() throws Exception {
        String path = "file:///path/to/prescription-type.xml";
        MimeTypes typeDatabase = MimeTypesFactory.create(new URL(path));
        Tika tika = new Tika(new CompositeDetector(typeDatabase, new EncryptedPrescriptionDetector()));
        return tika.detect("/path/to/tmp/prescription.xpd");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("customMimeInfo=" + customMimeInfo());
        System.out.println("customCompositeDetector=" + customCompositeDetector());
    }
}
