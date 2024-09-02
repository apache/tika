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
package org.apache.tika.pipes.async;

import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.digest.InputStreamDigester;

public class MockDigesterFactory implements DigestingParser.DigesterFactory {

    private boolean skipContainerDocument = false;

    @Override
    public DigestingParser.Digester build() {
        return new InputStreamDigester(1000000, "SHA-256", new MockEncoder());
    }

    private static class MockEncoder implements DigestingParser.Encoder {

        @Override
        public String encode(byte[] bytes) {
            StringBuilder hexString = new StringBuilder(2 * bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                String hex = Integer.toHexString(0xff & bytes[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }
    }

    @Override
    public void setSkipContainerDocument(boolean skipContainerDocument) {
        this.skipContainerDocument = skipContainerDocument;
    }

    @Override
    public boolean isSkipContainerDocument() {
        return skipContainerDocument;
    }
}
