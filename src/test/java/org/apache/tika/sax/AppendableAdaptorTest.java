/**
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
package org.apache.tika.sax;

import java.io.IOException;
import java.io.StringWriter;
import org.apache.tika.sax.WriteOutContentHandler;
import junit.framework.TestCase;

/**
 * Test case for {@link AppendableAdaptor}.
 *
 * @version $Revision$
 */
public class AppendableAdaptorTest extends TestCase {

    /**
     * Test {@link AppendableAdaptor#append(char)}.
     */
    public void testAppendChar() {
        StringWriter writer = new StringWriter();
        WriteOutContentHandler handler = new WriteOutContentHandler(writer);
        Appendable appendable = new AppendableAdaptor(handler);

        try {
            appendable.append('F').append('o').append('o');
        } catch (Throwable t) {
            fail("Threw: " + t);
        }
        assertEquals("Foo", writer.toString());
    }

    /**
     * Test {@link AppendableAdaptor#append(String)}.
     */
    public void testAppendString() {
        StringWriter writer = new StringWriter();
        WriteOutContentHandler handler = new WriteOutContentHandler(writer);
        Appendable appendable = new AppendableAdaptor(handler);

        try {
            appendable.append("Foo").append("Bar");
        } catch (Throwable t) {
            fail("Threw: " + t);
        }
        assertEquals("FooBar", writer.toString());
    }

    /**
     * Test {@link AppendableAdaptor#append(String)}.
     */
    public void testAppendStringBuilder() {
        StringWriter writer = new StringWriter();
        WriteOutContentHandler handler = new WriteOutContentHandler(writer);
        Appendable appendable = new AppendableAdaptor(handler);

        try {
            appendable.append(new StringBuilder("Foo"))
                      .append(new StringBuilder("Bar"));
        } catch (Throwable t) {
            fail("Threw: " + t);
        }
        assertEquals("FooBar", writer.toString());
    }

    /**
     * Test {@link AppendableAdaptor#append(String, int, int)}.
     */
    public void testAppendPortion() {
        StringWriter writer = new StringWriter();
        WriteOutContentHandler handler = new WriteOutContentHandler(writer);
        Appendable appendable = new AppendableAdaptor(handler);

        try {
            appendable.append("12345", 1, 3).append("ABC", 2, 3);
        } catch (Throwable t) {
            fail("Threw: " + t);
        }
        assertEquals("23C", writer.toString());
    }

    /**
     * Test errors
     */
    public void testErrors() {
        try {
            new AppendableAdaptor(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }

        WriteOutContentHandler handler = new WriteOutContentHandler(new StringWriter());
        Appendable appendable = new AppendableAdaptor(handler);

        try {
            appendable.append("123", 2, 8);
            fail("End too big, expected IndexOutOfBoundsException");
        } catch (IOException e) {
            fail("Threw: " + e);
        } catch (IndexOutOfBoundsException e) {
            // expected result
        }

        try {
            appendable.append("123", 5, 3);
            fail("Start too big, expected IndexOutOfBoundsException");
        } catch (IOException e) {
            fail("Threw: " + e);
        } catch (IndexOutOfBoundsException e) {
            // expected result
        }
    }

}
