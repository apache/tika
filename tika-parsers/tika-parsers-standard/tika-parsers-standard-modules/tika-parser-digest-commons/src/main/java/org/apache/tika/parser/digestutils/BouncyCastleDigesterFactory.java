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
package org.apache.tika.parser.digestutils;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.digest.DigestDef;
import org.apache.tika.digest.Digester;
import org.apache.tika.digest.DigesterFactory;

/**
 * Factory for {@link BouncyCastleDigester} with configurable algorithms and encodings.
 * <p>
 * Default: markLimit = 1000000, MD5 with HEX encoding.
 * <p>
 * BouncyCastle supports additional algorithms beyond the standard Java ones,
 * such as SHA3-256, SHA3-384, SHA3-512.
 * <p>
 * Example JSON configuration:
 * <pre>
 * {
 *   "digesterFactory": {
 *     "bouncy-castle-digester-factory": {
 *       "markLimit": 1000000,
 *       "digests": [
 *         { "algorithm": "MD5" },
 *         { "algorithm": "SHA3_256", "encoding": "BASE32" }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
@TikaComponent
public class BouncyCastleDigesterFactory implements DigesterFactory {

    private int markLimit = 1000000;
    private List<DigestDef> digests = new ArrayList<>();

    public BouncyCastleDigesterFactory() {
        digests.add(new DigestDef(DigestDef.Algorithm.MD5));
    }

    @Override
    public Digester build() {
        return new BouncyCastleDigester(markLimit, digests);
    }

    public int getMarkLimit() {
        return markLimit;
    }

    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }

    public List<DigestDef> getDigests() {
        return digests;
    }

    public void setDigests(List<DigestDef> digests) {
        this.digests = digests;
    }
}
