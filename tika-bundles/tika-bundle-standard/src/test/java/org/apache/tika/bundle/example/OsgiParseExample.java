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
package org.apache.tika.bundle.example;

import java.nio.file.Path;

import org.osgi.framework.BundleContext;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * The example on the "Using Tika in OSGi" docs page; BundleIT installs it as a bundle and runs it.
 */
public class OsgiParseExample {

    public static String parse(BundleContext context, Path path) throws Exception {
        // tag::parse[]
        Detector detector = context.getService(context.getServiceReference(Detector.class));
        Parser standard = context.getService(context.getServiceReference(Parser.class));
        Parser parser = new AutoDetectParser(detector, standard);

        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, parser);   // parse embedded documents too
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(path)) {
            parser.parse(tis, handler, metadata, parseContext);
        }
        // end::parse[]
        return handler.toString();
    }
}
