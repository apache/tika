/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.recognition.tf;

import org.apache.tika.config.Param;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Ignore
public class TensorflowVideoRecParserTest {

    @Test
    public void recognise() throws Exception {
        TensorflowRESTVideoRecogniser recogniser = new TensorflowRESTVideoRecogniser();
        recogniser.initialize(new HashMap<String, Param>());
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("test-documents/testVideoMp4.mp4")) {
            List<RecognisedObject> objects = recogniser.recognise(stream, new DefaultHandler(), new Metadata(), new ParseContext());
            
            Assert.assertTrue(objects.size() > 0);
            Set<String> objectLabels = new HashSet<>();
            for (RecognisedObject object : objects) {
                objectLabels.add(object.getLabel());
            }
            Assert.assertTrue(objectLabels.size() > 0);
        }
    }

}