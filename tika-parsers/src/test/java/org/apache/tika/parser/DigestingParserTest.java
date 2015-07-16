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
package org.apache.tika.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.utils.CommonsDigester;
import org.junit.Test;


public class DigestingParserTest extends TikaTest {

    private final static String P = TikaCoreProperties.TIKA_META_PREFIX+
            "digest"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    private final int UNLIMITED = 1000000;//well, not really, but longer than input file
    private final Parser p = new AutoDetectParser();

    @Test
    public void testBasic() throws Exception {
        Map<CommonsDigester.DigestAlgorithm, String> expected =
                new HashMap<CommonsDigester.DigestAlgorithm, String>();

        expected.put(CommonsDigester.DigestAlgorithm.MD2,"d768c8e27b0b52c6eaabfaa7122d1d4f");
        expected.put(CommonsDigester.DigestAlgorithm.MD5,"59f626e09a8c16ab6dbc2800c685f772");
        expected.put(CommonsDigester.DigestAlgorithm.SHA1,"7a1f001d163ac90d8ea54c050faf5a38079788a6");
        expected.put(CommonsDigester.DigestAlgorithm.SHA256,"c4b7fab030a8b6a9d6691f6699ac8e6f" +
                                                            "82bc53764a0f1430d134ae3b70c32654");
        expected.put(CommonsDigester.DigestAlgorithm.SHA384,"ebe368b9326fef44408290724d187553"+
                                                            "8b8a6923fdf251ddab72c6e4b5d54160" +
                                                            "9db917ba4260d1767995a844d8d654df");
        expected.put(CommonsDigester.DigestAlgorithm.SHA512,"ee46d973ee1852c018580c242955974d"+
                                                            "da4c21f36b54d7acd06fcf68e974663b"+
                                                            "fed1d256875be58d22beacf178154cc3"+
                                                            "a1178cb73443deaa53aa0840324708bb");

        //test each one
        for (CommonsDigester.DigestAlgorithm algo : CommonsDigester.DigestAlgorithm.values()) {
            Metadata m = new Metadata();
            XMLResult xml = getXML("test_recursive_embedded.docx",
                    new DigestingParser(p, new CommonsDigester(UNLIMITED, algo)), m);
            assertEquals(algo.toString(), expected.get(algo), m.get(P + algo.toString()));
        }


        //test comma separated
        CommonsDigester.DigestAlgorithm[] algos = CommonsDigester.parse("md5,sha256,sha384,sha512");
        Metadata m = new Metadata();
        XMLResult xml = getXML("test_recursive_embedded.docx",
                new DigestingParser(p, new CommonsDigester(UNLIMITED, algos)), m);
        for (CommonsDigester.DigestAlgorithm algo : new CommonsDigester.DigestAlgorithm[]{
                CommonsDigester.DigestAlgorithm.MD5,
                CommonsDigester.DigestAlgorithm.SHA256,
                CommonsDigester.DigestAlgorithm.SHA384,
                CommonsDigester.DigestAlgorithm.SHA512}) {
            assertEquals(algo.toString(), expected.get(algo), m.get(P + algo.toString()));
        }

        assertNull(m.get(P+CommonsDigester.DigestAlgorithm.MD2.toString()));
        assertNull(m.get(P+CommonsDigester.DigestAlgorithm.SHA1.toString()));

    }

    @Test
    public void testLimitedRead() throws Exception {
        CommonsDigester.DigestAlgorithm algo = CommonsDigester.DigestAlgorithm.MD5;
        int limit = 100;
        byte[] bytes = new byte[limit];
        InputStream is = getResourceAsStream("/test-documents/test_recursive_embedded.docx");
        is.read(bytes, 0, limit);
        is.close();
        Metadata m = new Metadata();
        try {
            XMLResult xml = getXML(TikaInputStream.get(bytes),
                    new DigestingParser(p, new CommonsDigester(100, algo)), m);
        } catch (TikaException e) {
            //thrown because this is just a file fragment
            assertContains("Unexpected RuntimeException from org.apache.tika.parser.microsoft.ooxml.OOXMLParser",
                    e.getMessage());
        }
        String expectedMD5 = m.get(P+"MD5");

        m = new Metadata();
        XMLResult xml = getXML("test_recursive_embedded.docx",
                new DigestingParser(p, new CommonsDigester(100, algo)), m);
        assertEquals(expectedMD5, m.get(P+"MD5"));
    }

    @Test
    public void testReset() throws Exception {
        String expectedMD5 = "1643c2cef21e36720c54f4f6cb3349d0";
        Metadata m = new Metadata();
        XMLResult xml = getXML("test_recursive_embedded.docx",
                new DigestingParser(p, new CommonsDigester(100, CommonsDigester.DigestAlgorithm.MD5)), m);
        assertEquals(expectedMD5, m.get(P+"MD5"));
    }

    @Test
    public void testNegativeMaxMarkLength() throws Exception {
        Metadata m = new Metadata();
        boolean ex = false;
        try {
            XMLResult xml = getXML("test_recursive_embedded.docx",
                    new DigestingParser(p, new CommonsDigester(-1, CommonsDigester.DigestAlgorithm.MD5)), m);
        } catch (IllegalArgumentException e) {
            ex = true;
        }
        assertTrue("Exception not thrown", ex);
    }

}
