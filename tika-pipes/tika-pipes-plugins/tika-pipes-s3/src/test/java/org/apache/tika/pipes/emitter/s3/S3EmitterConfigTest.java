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
package org.apache.tika.pipes.emitter.s3;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class S3EmitterConfigTest {

    @Test
    public void testIdentityIsValid() throws Exception {
        S3EmitterConfig config = load("identity");
        assertDoesNotThrow(config::validate);
    }

    @Test
    public void testUnknownProviderIsInvalid() throws Exception {
        S3EmitterConfig config = load("bogus");
        TikaConfigException e = assertThrows(TikaConfigException.class, config::validate);
        assertTrue(e.getMessage().contains("'identity'"), e.getMessage());
    }

    private static S3EmitterConfig load(String credentialsProvider) throws TikaConfigException {
        return S3EmitterConfig.load("{\"bucket\": \"bucket\", \"region\": \"us-east-1\", "
                + "\"credentialsProvider\": \"" + credentialsProvider + "\"}");
    }
}
