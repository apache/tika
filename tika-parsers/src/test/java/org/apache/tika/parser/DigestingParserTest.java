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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.utils.CommonsDigester;
import org.junit.Test;


public class DigestingParserTest extends TikaTest {

    private final static String P = TikaCoreProperties.TIKA_META_PREFIX+
            "digest"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    private final static int UNLIMITED = 1000000;//well, not really, but longer than input file

    private final static long SEED = new Random().nextLong();

    private final Random random = new Random(SEED);
    private final Parser p = new AutoDetectParser();

    @Test
    public void testBasic() throws Exception {
        Map<CommonsDigester.DigestAlgorithm, String> expected =
                new HashMap<>();

        expected.put(CommonsDigester.DigestAlgorithm.MD2, "d768c8e27b0b52c6eaabfaa7122d1d4f");
        expected.put(CommonsDigester.DigestAlgorithm.MD5, "59f626e09a8c16ab6dbc2800c685f772");
        expected.put(CommonsDigester.DigestAlgorithm.SHA1, "7a1f001d163ac90d8ea54c050faf5a38079788a6");
        expected.put(CommonsDigester.DigestAlgorithm.SHA256, "c4b7fab030a8b6a9d6691f6699ac8e6f" +
                "82bc53764a0f1430d134ae3b70c32654");
        expected.put(CommonsDigester.DigestAlgorithm.SHA384, "ebe368b9326fef44408290724d187553" +
                "8b8a6923fdf251ddab72c6e4b5d54160" +
                "9db917ba4260d1767995a844d8d654df");
        expected.put(CommonsDigester.DigestAlgorithm.SHA512, "ee46d973ee1852c018580c242955974d" +
                "da4c21f36b54d7acd06fcf68e974663b" +
                "fed1d256875be58d22beacf178154cc3" +
                "a1178cb73443deaa53aa0840324708bb");

        //test each one
        for (CommonsDigester.DigestAlgorithm algo : CommonsDigester.DigestAlgorithm.values()) {
            Metadata m = new Metadata();
            XMLResult xml = getXML("test_recursive_embedded.docx",
                    new DigestingParser(p, new CommonsDigester(UNLIMITED, algo)), m);
            assertEquals(algo.toString(), expected.get(algo), m.get(P + algo.toString()));
        }

    }

    @Test
    public void testCommaSeparated() throws Exception {
        Map<CommonsDigester.DigestAlgorithm, String> expected =
                new HashMap<>();


        expected.put(CommonsDigester.DigestAlgorithm.MD2, "d768c8e27b0b52c6eaabfaa7122d1d4f");
        expected.put(CommonsDigester.DigestAlgorithm.MD5, "59f626e09a8c16ab6dbc2800c685f772");
        expected.put(CommonsDigester.DigestAlgorithm.SHA1, "PIPQAHIWHLEQ3DVFJQCQ7L22HADZPCFG");
        expected.put(CommonsDigester.DigestAlgorithm.SHA256, "c4b7fab030a8b6a9d6691f6699ac8e6f" +
                "82bc53764a0f1430d134ae3b70c32654");
        expected.put(CommonsDigester.DigestAlgorithm.SHA384, "ebe368b9326fef44408290724d187553" +
                "8b8a6923fdf251ddab72c6e4b5d54160" +
                "9db917ba4260d1767995a844d8d654df");
        expected.put(CommonsDigester.DigestAlgorithm.SHA512, "ee46d973ee1852c018580c242955974d" +
                "da4c21f36b54d7acd06fcf68e974663b" +
                "fed1d256875be58d22beacf178154cc3" +
                "a1178cb73443deaa53aa0840324708bb");

        //test comma separated
        Metadata m = new Metadata();
        XMLResult xml = getXML("test_recursive_embedded.docx",
                new DigestingParser(p, new CommonsDigester(UNLIMITED,
                        "md5,sha256,sha384,sha512,sha1:32")), m);
        for (CommonsDigester.DigestAlgorithm algo : new CommonsDigester.DigestAlgorithm[]{
                CommonsDigester.DigestAlgorithm.MD5,
                CommonsDigester.DigestAlgorithm.SHA1,
                CommonsDigester.DigestAlgorithm.SHA256,
                CommonsDigester.DigestAlgorithm.SHA384,
                CommonsDigester.DigestAlgorithm.SHA512}) {
            assertEquals(algo.toString(), expected.get(algo), m.get(P + algo.toString()));
        }

        assertNull(m.get(P+CommonsDigester.DigestAlgorithm.MD2.toString()));
    }

    @Test
    public void testReset() throws Exception {
        String expectedMD5 = "59f626e09a8c16ab6dbc2800c685f772";
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

    @Test
    public void testMultipleCombinations() throws Exception {
        Path tmp = Files.createTempFile("tika-digesting-parser-test", "");

        try {
            //try some random lengths
            for (int i = 0; i < 10; i++) {
                testMulti(tmp, random.nextInt(100000), random.nextInt(100000), random.nextBoolean());
            }
            //try specific lengths
            testMulti(tmp, 1000, 100000, true);
            testMulti(tmp, 1000, 100000, false);
            testMulti(tmp, 10000, 10001, true);
            testMulti(tmp, 10000, 10001, false);
            testMulti(tmp, 10000, 10000, true);
            testMulti(tmp, 10000, 10000, false);
            testMulti(tmp, 10000, 9999, true);
            testMulti(tmp, 10000, 9999, false);


            testMulti(tmp, 1000, 100, true);
            testMulti(tmp, 1000, 100, false);
            testMulti(tmp, 1000, 10, true);
            testMulti(tmp, 1000, 10, false);
            testMulti(tmp, 1000, 0, true);
            testMulti(tmp, 1000, 0, false);

            testMulti(tmp, 0, 100, true);
            testMulti(tmp, 0, 100, false);

        } finally {
            Files.delete(tmp);
        }
    }

    private void testMulti(Path tmp, int fileLength, int markLimit,
                           boolean useTikaInputStream) throws IOException {

        OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp,
                StandardOpenOption.CREATE));

        for (int i = 0; i < fileLength; i++) {
            os.write(random.nextInt());
        }
        os.flush();
        os.close();

        Metadata truth = new Metadata();
        addTruth(tmp, CommonsDigester.DigestAlgorithm.MD5, truth);
        addTruth(tmp, CommonsDigester.DigestAlgorithm.SHA1, truth);
        addTruth(tmp, CommonsDigester.DigestAlgorithm.SHA512, truth);


        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                CommonsDigester.DigestAlgorithm.SHA512,
                CommonsDigester.DigestAlgorithm.SHA1,
                CommonsDigester.DigestAlgorithm.MD5);

        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                CommonsDigester.DigestAlgorithm.MD5,
                CommonsDigester.DigestAlgorithm.SHA1);

        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                CommonsDigester.DigestAlgorithm.SHA1,
                CommonsDigester.DigestAlgorithm.SHA512,
                CommonsDigester.DigestAlgorithm.MD5);

        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                CommonsDigester.DigestAlgorithm.SHA1);

        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                CommonsDigester.DigestAlgorithm.MD5);

    }

    private void checkMulti(Metadata truth, Path tmp,
                            int fileLength, int markLimit,
                            boolean useTikaInputStream, CommonsDigester.DigestAlgorithm... algos) throws IOException {
        Metadata result = new Metadata();
        CommonsDigester digester = new CommonsDigester(markLimit, algos);
        try (InputStream is = useTikaInputStream ? TikaInputStream.get(tmp) :
                new BufferedInputStream(Files.newInputStream(tmp))) {
            digester.digest(is, result, new ParseContext());
        }

        for (CommonsDigester.DigestAlgorithm algo : algos) {
            String truthValue = truth.get(P+algo.name());
            String resultValue = result.get(P+algo.name());
            assertNotNull("truth", truthValue);
            assertNotNull("result (fileLength="+fileLength+", markLimit="+markLimit+")",
                    resultValue);
            assertEquals("fileLength("+fileLength+") markLimit("+
                    markLimit+") useTikaInputStream("+useTikaInputStream+") "+
                    "algorithm("+algo.name()+") seed("+SEED+")",
                    truthValue, resultValue);
        }

    }

    private void addTruth(Path tmp, CommonsDigester.DigestAlgorithm algo, Metadata truth) throws IOException {
        String digest = null;
        try (InputStream is = Files.newInputStream(tmp)) {
            switch (algo) {
                case MD2:
                    digest = DigestUtils.md2Hex(is);
                    break;
                case MD5:
                    digest = DigestUtils.md5Hex(is);
                    break;
                case SHA1:
                    digest = DigestUtils.sha1Hex(is);
                    break;
                case SHA256:
                    digest = DigestUtils.sha256Hex(is);
                    break;
                case SHA384:
                    digest = DigestUtils.sha384Hex(is);
                    break;
                case SHA512:
                    digest = DigestUtils.sha512Hex(is);
                    break;
                default:
                    throw new IllegalArgumentException("Sorry, not aware of algorithm: " + algo.toString());
            }
        }
        truth.set(P+algo.name(), digest);

    }


}
