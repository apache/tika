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
import org.apache.commons.lang.StringUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.utils.BouncyCastleDigester;
import org.junit.Test;


public class BouncyCastleDigestingParserTest extends TikaTest {

    private final static String P = TikaCoreProperties.TIKA_META_PREFIX+
            "digest"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    private final static int UNLIMITED = 1000000;//well, not really, but longer than input file

    private final static long SEED = new Random().nextLong();

    private final Random random = new Random(SEED);
    private final Parser p = new AutoDetectParser();

    @Test
    public void testBasic() throws Exception {
        Map<String, String> expected =
                new HashMap<>();

        expected.put("MD2", "d768c8e27b0b52c6eaabfaa7122d1d4f");
        expected.put("MD5", "59f626e09a8c16ab6dbc2800c685f772");
        expected.put("SHA1", "7a1f001d163ac90d8ea54c050faf5a38079788a6");
        expected.put("SHA256", "c4b7fab030a8b6a9d6691f6699ac8e6f" +
                "82bc53764a0f1430d134ae3b70c32654");
        expected.put("SHA384", "ebe368b9326fef44408290724d187553" +
                "8b8a6923fdf251ddab72c6e4b5d54160" +
                "9db917ba4260d1767995a844d8d654df");
        expected.put("SHA512", "ee46d973ee1852c018580c242955974d" +
                "da4c21f36b54d7acd06fcf68e974663b" +
                "fed1d256875be58d22beacf178154cc3" +
                "a1178cb73443deaa53aa0840324708bb");

        //test each one
        for (String algo : expected.keySet()) {
            Metadata m = new Metadata();
            XMLResult xml = getXML("test_recursive_embedded.docx",
                    new DigestingParser(p, new BouncyCastleDigester(UNLIMITED, algo)), m);
            assertEquals(algo, expected.get(algo), m.get(P + algo));
        }

    }

    @Test
    public void testCommaSeparated() throws Exception {
        Map<String, String> expected =
                new HashMap<>();

        expected.put("MD2", "d768c8e27b0b52c6eaabfaa7122d1d4f");
        expected.put("MD5", "59f626e09a8c16ab6dbc2800c685f772");
        expected.put("SHA1", "7a1f001d163ac90d8ea54c050faf5a38079788a6");
        expected.put("SHA256", "c4b7fab030a8b6a9d6691f6699ac8e6f" +
                "82bc53764a0f1430d134ae3b70c32654");
        expected.put("SHA384", "ebe368b9326fef44408290724d187553" +
                "8b8a6923fdf251ddab72c6e4b5d54160" +
                "9db917ba4260d1767995a844d8d654df");
        expected.put("SHA512",
                "ee46d973ee1852c018580c242955974d" +
                "da4c21f36b54d7acd06fcf68e974663b" +
                "fed1d256875be58d22beacf178154cc3" +
                "a1178cb73443deaa53aa0840324708bb");
        expected.put("SHA3-512",
                "04337f667a250348a1acb992863b3ddc"+
                "eab38365c206c18d356d2b31675ad669"+
                "5fb5497f4e79b11640aefbb8042a5dbb"+
                "7ec6c2c6c1b6e19210453591c52cb6eb");
        expected.put("SHA1", "PIPQAHIWHLEQ3DVFJQCQ7L22HADZPCFG");
        //test comma separated
        Metadata m = new Metadata();
        XMLResult xml = getXML("test_recursive_embedded.docx",
                new DigestingParser(p, new BouncyCastleDigester(UNLIMITED,
                        "MD5,SHA256,SHA384,SHA512,SHA3-512,SHA1:32")), m);
        for (String algo : new String[]{
                "MD5", "SHA256", "SHA384", "SHA512", "SHA3-512",
                "SHA1"
        }) {
            assertEquals(algo, expected.get(algo), m.get(P + algo));
        }

        assertNull(m.get(P+"MD2"));

    }

    @Test
    public void testReset() throws Exception {
        String expectedMD5 = "59f626e09a8c16ab6dbc2800c685f772";
        Metadata m = new Metadata();
        XMLResult xml = getXML("test_recursive_embedded.docx",
                new DigestingParser(p, new BouncyCastleDigester(100, "MD5")), m);
        assertEquals(expectedMD5, m.get(P+"MD5"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeMaxMarkLength() throws Exception {
        getXML("test_recursive_embedded.docx",
                    new DigestingParser(p,
                            new BouncyCastleDigester(-1, "MD5")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnrecognizedEncodingOptions() throws Exception {
        getXML("test_recursive_embedded.docx",
                new DigestingParser(p,
                        new BouncyCastleDigester(100000,
                                "MD5:33")));
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
        addTruth(tmp, "MD5", truth);
        addTruth(tmp, "SHA1", truth);
        addTruth(tmp, "SHA512", truth);


        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                "SHA512",
                "SHA1", "MD5");
        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                "MD5", "SHA1");

        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                "SHA1", "SHA512", "MD5");
        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                "SHA1");

        checkMulti(truth, tmp, fileLength, markLimit, useTikaInputStream,
                "MD5");

    }

    private void checkMulti(Metadata truth, Path tmp,
                            int fileLength, int markLimit,
                            boolean useTikaInputStream,
                            String... algos) throws IOException {
        Metadata result = new Metadata();
        BouncyCastleDigester digester = new BouncyCastleDigester(markLimit,
                StringUtils.join(algos, ","));
        try (InputStream is = useTikaInputStream ? TikaInputStream.get(tmp) :
                new BufferedInputStream(Files.newInputStream(tmp))) {
            digester.digest(is, result, new ParseContext());
        }

        for (String algo : algos) {
            String truthValue = truth.get(P+algo);
            String resultValue = result.get(P+algo);
            assertNotNull("truth", truthValue);
            assertNotNull("result (fileLength="+fileLength+", markLimit="+markLimit+")",
                    resultValue);
            assertEquals("fileLength("+fileLength+") markLimit("+
                    markLimit+") useTikaInputStream("+useTikaInputStream+") "+
                    "algorithm("+algo+") seed("+SEED+")",
                    truthValue, resultValue);
        }

    }

    private void addTruth(Path tmp, String algo, Metadata truth) throws IOException {
        String digest = null;
        //for now, rely on CommonsDigest for truth
        try (InputStream is = Files.newInputStream(tmp)) {
            if ("MD2".equals(algo)) {
                digest = DigestUtils.md2Hex(is);
            } else if ("MD5".equals(algo)) {
                digest = DigestUtils.md5Hex(is);
            } else if ("SHA1".equals(algo)) {
                digest = DigestUtils.sha1Hex(is);
            } else if ("SHA256".equals(algo)) {
                digest = DigestUtils.sha256Hex(is);
            } else if ("SHA384".equals(algo)) {
                digest = DigestUtils.sha384Hex(is);
            } else if ("SHA512".equals(algo)) {
                digest = DigestUtils.sha512Hex(is);
            } else {
                throw new IllegalArgumentException("Sorry, not aware of algorithm: " + algo);
            }
        }
        truth.set(P+algo, digest);

    }


}
