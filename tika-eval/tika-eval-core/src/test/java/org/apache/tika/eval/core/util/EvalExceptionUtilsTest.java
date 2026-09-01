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
package org.apache.tika.eval.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EvalExceptionUtilsTest {

    @Test
    public void fullTraceLosesMessagesOnly() {
        String trace = "org.apache.tika.exception.TikaException: secret /path\n"
                + "\tat org.apache.tika.Foo.bar(Foo.java:12)\n"
                + "Caused by: java.io.IOException: other secret\n"
                + "\tat org.apache.tika.Foo.baz(Foo.java:34)\n";
        assertEquals("o.a.t.exception.TikaException\n"
                + "\tat o.a.t.Foo.bar(Foo.java:12)\n"
                + "Caused by: java.io.IOException\n"
                + "\tat o.a.t.Foo.baz(Foo.java:34)\n", EvalExceptionUtils.normalize(trace));
    }

    @Test
    public void messageRedactedTraceNormalizesToSameKey() {
        // The redacted "Caused by:" line has no colon; the snipper must not run past the
        // newline into the following frame.
        String full = "org.apache.tika.exception.TikaException: secret\n"
                + "\tat org.apache.tika.Foo.bar(Foo.java:12)\n"
                + "Caused by: java.io.IOException: other secret\n"
                + "\tat org.apache.tika.Foo.baz(Foo.java:34)\n";
        String redacted = "org.apache.tika.exception.TikaException\n"
                + "\tat org.apache.tika.Foo.bar(Foo.java:12)\n"
                + "Caused by: java.io.IOException\n"
                + "\tat org.apache.tika.Foo.baz(Foo.java:34)\n";
        assertEquals(EvalExceptionUtils.normalize(full), EvalExceptionUtils.normalize(redacted));
    }

    @Test
    public void suppressedCauseMessageIsSnipped() {
        // printStackTrace tab-indents a suppressed exception's cause; traces differing
        // only in that runtime detail must normalize to the same key.
        String a = "org.apache.tika.exception.TikaException: top\n"
                + "\tSuppressed: java.io.IOException: close failed\n"
                + "\t\tat org.apache.tika.Foo.close(Foo.java:56)\n"
                + "\tCaused by: java.nio.file.NoSuchFileException: /tmp/spool-123.tmp\n"
                + "\tat org.apache.tika.Foo.bar(Foo.java:12)\n";
        String b = a.replace("/tmp/spool-123.tmp", "/tmp/spool-456.tmp");
        assertEquals(EvalExceptionUtils.normalize(a), EvalExceptionUtils.normalize(b));
    }
}
