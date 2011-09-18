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

package org.apache.tika.parser.chm;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class TestChmDocumentInformation extends TestCase {
    private CHMDocumentInformation chmDoc = null;

    public void setUp() throws Exception {
        chmDoc = CHMDocumentInformation.load(
                new ByteArrayInputStream(TestParameters.chmData));
    }

    public void testGetCHMDocInformation() throws TikaException, IOException {
        Metadata md = new Metadata();
        chmDoc.getCHMDocInformation(md);
        Assert.assertEquals(TestParameters.VP_CHM_MIME_TYPE, md.toString()
                .trim());
    }

    public void testGetText() throws TikaException {
        Assert.assertTrue(chmDoc.getText().contains(
                "The TCard method accepts only numeric arguments"));
    }

    public void tearDown() throws Exception {
    }

}
