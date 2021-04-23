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
package org.apache.tika.eval.core.textstats;

import java.security.MessageDigest;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Calculates the base32 encoded SHA-256 checksum on the analyzed text
 */
public class TextSha256Signature implements BytesRefCalculator<String> {

    static Base32 BASE32 = new Base32();

    @Override
    public BytesRefCalcInstance<String> getInstance() {
        return new TextSha256Instance();
    }

    class TextSha256Instance implements BytesRefCalcInstance<String> {
        private MessageDigest messageDigest = DigestUtils.getSha256Digest();

        @Override
        public void update(byte[] bytes, int start, int len) {
            messageDigest.update(bytes, start, len);

        }

        @Override
        public String finish() {
            return BASE32.encodeAsString(messageDigest.digest());
        }

        @Override
        public Class getOuterClass() {
            return TextSha256Signature.class;
        }
    }
}
