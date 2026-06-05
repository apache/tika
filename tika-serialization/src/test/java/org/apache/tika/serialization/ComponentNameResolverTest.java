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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ComponentNameResolverTest {

    /**
     * Resolving an unknown component name must produce an actionable message: it should
     * call out the two usual causes (a typo, or the providing module not being on the
     * classpath -- the TIKA-4750 scenario for tess4j-parser) rather than just stating
     * the registration rule.
     */
    @Test
    public void unregisteredComponentGivesActionableMessage() {
        ClassNotFoundException e = assertThrows(ClassNotFoundException.class,
                () -> ComponentNameResolver.resolveClass(
                        "definitely-not-a-real-parser-xyz", getClass().getClassLoader()));
        String msg = e.getMessage();
        assertTrue(msg.contains("definitely-not-a-real-parser-xyz"), msg);
        assertTrue(msg.contains("misspelled"), msg);
        assertTrue(msg.contains("not on the classpath"), msg);
        // names the opt-in-module cause concretely so users know what to add
        assertTrue(msg.contains("tika-parser-tess4j-module"), msg);
        assertTrue(msg.contains("Arbitrary class names are not allowed"), msg);
    }
}
