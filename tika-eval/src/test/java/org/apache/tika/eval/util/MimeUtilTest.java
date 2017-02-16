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

package org.apache.tika.eval.util;


import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Fix mimetype.getExtension to work with these and then we can get rid of MimeUtil")
public class MimeUtilTest {

    private final TikaConfig config = TikaConfig.getDefaultConfig();

    @Test
    public void testBasic() throws Exception {
        assertResult("application/pdf", ".pdf");
        assertResult("APPLICATION/PDF", ".pdf");
        assertResult("text/plain; charset=ISO-8859-1", ".txt");
        assertResult("application/xhtml+xml; charset=UTF-8\n", ".html");
        assertResult("application/xml; charset=UTF-8\n", ".xml");

        assertException("bogosity", "xml");
    }

    private void assertException(String contentType, String expected) {
        boolean ex = false;
        try {
            assertResult(contentType, expected);
        } catch (MimeTypeException e) {
            ex = true;
        }
        assertTrue("Should have had exception for: " + contentType, ex);
    }

    private void assertResult(String contentType, String expected) throws MimeTypeException {
        TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
        MimeTypes r = tikaConfig.getMimeRepository();
        MimeType mt = r.forName(contentType);

//        String ext = MimeUtil.getExtension(contentType, config);
        assertEquals(expected, mt.getExtension());
    }
}
