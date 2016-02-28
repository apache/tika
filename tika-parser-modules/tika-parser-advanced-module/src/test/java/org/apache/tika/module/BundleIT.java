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
package org.apache.tika.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.Dictionary;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.osgi.TikaService;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.xml.sax.ContentHandler;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class BundleIT {

    private static final String BUNDLE_JAR_SYS_PROP = "project.bundle.file";
    
    private static final String TIKA_CORE_VERSION = "tika.core.version";
    
    @Inject
    private BundleContext bc;

    @Configuration
    public Option[] configuration() throws IOException, URISyntaxException {
        String bundleFileName = System.getProperty(BUNDLE_JAR_SYS_PROP);
        String tikaCoreVersion = System.getProperty(TIKA_CORE_VERSION);
        return options(junitBundles(), mavenBundle("org.apache.tika", "tika-core", tikaCoreVersion),
                bundle(new File(bundleFileName).toURI().toString()));
    }

    @Test
    public void testBundleLoaded() throws Exception {
        boolean hasCore = false, hasBundle = false;
        for (Bundle b : bc.getBundles()) {
            if ("org.apache.tika.core".equals(b.getSymbolicName())) {
                hasCore = true;
                assertEquals("Core not activated", Bundle.ACTIVE, b.getState());
            }
            if ("org.apache.tika.parser-advanced-module".equals(b.getSymbolicName())) {
                hasBundle = true;
                assertEquals("Bundle not activated", Bundle.ACTIVE, b.getState());
            }
        }
        assertTrue("Core bundle not found", hasCore);
        assertTrue("Advanced bundle not found", hasBundle);
    }
}
