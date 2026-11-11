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
package org.apache.tika.serialization.serdes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaObjectMapperFactory;

public class UnreadBlockWarningTest {

    @BeforeAll
    public static void loadComponentRegistries() {
        // The registries are loaded with the shared mapper. Load them here so the test does
        // not depend on an earlier test in the same JVM having done it.
        TikaObjectMapperFactory.getMapper();
    }

    @Test
    public void testWarnsOncePerComponent() {
        // a fixture no other test sends a block for, so the first call is this one
        assertTrue(ParseContextDeserializer.warnIfUnread("unread-warning-test-parser"));
        assertFalse(ParseContextDeserializer.warnIfUnread("unread-warning-test-parser"));
        assertFalse(ParseContextDeserializer.warnIfUnread("request-config-test-parser"));
        assertFalse(ParseContextDeserializer.warnIfUnread("no-such-component"));
    }
}
