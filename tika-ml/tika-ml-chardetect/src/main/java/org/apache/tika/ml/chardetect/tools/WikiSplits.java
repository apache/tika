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
package org.apache.tika.ml.chardetect.tools;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Article splits of the Wikipedia sample (fetch_lang.py): bucket = sha1("lang:id") mod 10;
 * 0 test, 1 dev, 2-8 train, 9 sealed.  The sealed bucket is carved out of fetch_lang's train
 * split and is held back from training and from every eval until deliberately opened.
 */
public final class WikiSplits {

    public static final int SEALED_BUCKET = 9;

    private WikiSplits() {
    }

    public static int bucket(String lang, String id) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1")
                    .digest((lang + ":" + id).getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, d).mod(BigInteger.TEN).intValue();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The split to use: the stored one, except train articles in the sealed bucket. */
    public static String split(String lang, String id, String stored) {
        return "train".equals(stored) && bucket(lang, id) == SEALED_BUCKET ? "sealed" : stored;
    }
}
