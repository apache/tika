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
import java.net.URL;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Grabs a PDF file from a URL and prints its {@link Metadata}
 */
public class DisplayMetInstance {
    public static Metadata getMet(URL url) throws IOException, SAXException, TikaException {
        Metadata met = new Metadata();
        PDFParser parser = new PDFParser();
        parser.parse(url.openStream(), new BodyContentHandler(), met, new ParseContext());
        return met;
    }

    public static void main(String[] args) throws Exception {
        Metadata met = DisplayMetInstance.getMet(new URL(args[0]));
        System.out.println(met);
    }
}
