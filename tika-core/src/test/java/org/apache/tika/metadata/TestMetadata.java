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
package org.apache.tika.metadata;

//JDK imports
import java.util.Properties;

//Junit imports
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * JUnit based tests of class {@link org.apache.tika.metadata.Metadata}.
 */
public class TestMetadata extends TestCase {

    private static final String CONTENTTYPE = "contenttype";

    public TestMetadata(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(TestMetadata.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }

    /** Test for the <code>add(String, String)</code> method. */
    public void testAdd() {
        String[] values = null;
        Metadata meta = new Metadata();

        values = meta.getValues(CONTENTTYPE);
        assertEquals(0, values.length);

        meta.add(CONTENTTYPE, "value1");
        values = meta.getValues(CONTENTTYPE);
        assertEquals(1, values.length);
        assertEquals("value1", values[0]);

        meta.add(CONTENTTYPE, "value2");
        values = meta.getValues(CONTENTTYPE);
        assertEquals(2, values.length);
        assertEquals("value1", values[0]);
        assertEquals("value2", values[1]);

        // NOTE : For now, the same value can be added many times.
        // Should it be changed?
        meta.add(CONTENTTYPE, "value1");
        values = meta.getValues(CONTENTTYPE);
        assertEquals(3, values.length);
        assertEquals("value1", values[0]);
        assertEquals("value2", values[1]);
        assertEquals("value1", values[2]);
    }

    /** Test for the <code>set(String, String)</code> method. */
    public void testSet() {
        String[] values = null;
        Metadata meta = new Metadata();

        values = meta.getValues(CONTENTTYPE);
        assertEquals(0, values.length);

        meta.set(CONTENTTYPE, "value1");
        values = meta.getValues(CONTENTTYPE);
        assertEquals(1, values.length);
        assertEquals("value1", values[0]);

        meta.set(CONTENTTYPE, "value2");
        values = meta.getValues(CONTENTTYPE);
        assertEquals(1, values.length);
        assertEquals("value2", values[0]);

        meta.set(CONTENTTYPE, "new value 1");
        meta.add("contenttype", "new value 2");
        values = meta.getValues(CONTENTTYPE);
        assertEquals(2, values.length);
        assertEquals("new value 1", values[0]);
        assertEquals("new value 2", values[1]);
    }

    /** Test for <code>setAll(Properties)</code> method. */
    public void testSetProperties() {
        String[] values = null;
        Metadata meta = new Metadata();
        Properties props = new Properties();

        meta.setAll(props);
        assertEquals(0, meta.size());

        props.setProperty("name-one", "value1.1");
        meta.setAll(props);
        assertEquals(1, meta.size());
        values = meta.getValues("name-one");
        assertEquals(1, values.length);
        assertEquals("value1.1", values[0]);

        props.setProperty("name-two", "value2.1");
        meta.setAll(props);
        assertEquals(2, meta.size());
        values = meta.getValues("name-one");
        assertEquals(1, values.length);
        assertEquals("value1.1", values[0]);
        values = meta.getValues("name-two");
        assertEquals(1, values.length);
        assertEquals("value2.1", values[0]);
    }

    /** Test for <code>get(String)</code> method. */
    public void testGet() {
        Metadata meta = new Metadata();
        assertNull(meta.get("a-name"));
        meta.add("a-name", "value-1");
        assertEquals("value-1", meta.get("a-name"));
        meta.add("a-name", "value-2");
        assertEquals("value-1", meta.get("a-name"));
    }

    /** Test for <code>isMultiValued()</code> method. */
    public void testIsMultiValued() {
        Metadata meta = new Metadata();
        assertFalse(meta.isMultiValued("key"));
        meta.add("key", "value1");
        assertFalse(meta.isMultiValued("key"));
        meta.add("key", "value2");
        assertTrue(meta.isMultiValued("key"));
    }

    /** Test for <code>names</code> method. */
    public void testNames() {
        String[] names = null;
        Metadata meta = new Metadata();
        names = meta.names();
        assertEquals(0, names.length);

        meta.add("name-one", "value");
        names = meta.names();
        assertEquals(1, names.length);
        assertEquals("name-one", names[0]);
        meta.add("name-two", "value");
        names = meta.names();
        assertEquals(2, names.length);
    }

    /** Test for <code>remove(String)</code> method. */
    public void testRemove() {
        Metadata meta = new Metadata();
        meta.remove("name-one");
        assertEquals(0, meta.size());
        meta.add("name-one", "value-1.1");
        meta.add("name-one", "value-1.2");
        meta.add("name-two", "value-2.2");
        assertEquals(2, meta.size());
        assertNotNull(meta.get("name-one"));
        assertNotNull(meta.get("name-two"));
        meta.remove("name-one");
        assertEquals(1, meta.size());
        assertNull(meta.get("name-one"));
        assertNotNull(meta.get("name-two"));
        meta.remove("name-two");
        assertEquals(0, meta.size());
        assertNull(meta.get("name-one"));
        assertNull(meta.get("name-two"));
    }

    /** Test for <code>equals(Object)</code> method. */
    public void testObject() {
        Metadata meta1 = new Metadata();
        Metadata meta2 = new Metadata();
        assertFalse(meta1.equals(null));
        assertFalse(meta1.equals("String"));
        assertTrue(meta1.equals(meta2));
        meta1.add("name-one", "value-1.1");
        assertFalse(meta1.equals(meta2));
        meta2.add("name-one", "value-1.1");
        assertTrue(meta1.equals(meta2));
        meta1.add("name-one", "value-1.2");
        assertFalse(meta1.equals(meta2));
        meta2.add("name-one", "value-1.2");
        assertTrue(meta1.equals(meta2));
        meta1.add("name-two", "value-2.1");
        assertFalse(meta1.equals(meta2));
        meta2.add("name-two", "value-2.1");
        assertTrue(meta1.equals(meta2));
        meta1.add("name-two", "value-2.2");
        assertFalse(meta1.equals(meta2));
        meta2.add("name-two", "value-2.x");
        assertFalse(meta1.equals(meta2));
    }

}
