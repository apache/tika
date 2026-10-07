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
package org.apache.tika.parser.geogebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class GeoGebraJsonTest {

    private static List<String> ids(String json) throws IOException {
        return GeoGebraJson.elementIds(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testElementIdsInDocumentOrder() throws Exception {
        assertEquals(Arrays.asList("_slide1", "_slide0", "_slide2"), ids(
                "{\"id\":\"doc\",\"chapters\":[{\"id\":\"c1\",\"pages\":["
                        + "{\"id\":\"p1\",\"elements\":[{\"id\":\"_slide1\",\"type\":\"x\"},"
                        + "{\"id\":\"_slide0\"}]}]},"
                        + "{\"pages\":[{\"elements\":[{\"id\":\"_slide2\"}]}]}]}"));
    }

    @Test
    public void testIdsOutsideElementsIgnored() throws Exception {
        //ids on the document, chapter and page, nested deeper, and in other arrays
        assertEquals(Collections.emptyList(), ids(
                "{\"id\":\"doc\",\"chapters\":[{\"id\":\"c\",\"pages\":[{\"id\":\"p\","
                        + "\"elements\":[{\"meta\":{\"id\":\"deep\"}}],"
                        + "\"other\":[{\"id\":\"o\"}]}]}]}"));
        assertEquals(Collections.emptyList(), ids("{}"));
        assertEquals(Collections.emptyList(), ids("[]"));
    }

    @Test
    public void testMalformedStructureThrows() {
        assertThrows(IOException.class, () -> ids("not json"));
        assertThrows(IOException.class, () -> ids("{\"chapters\":[{\"pages\":["));
    }

    @Test
    public void testTextValuesAtAnyDepth() throws Exception {
        assertEquals(Arrays.asList("Hello\n", "World", "nested", "3"),
                GeoGebraJson.textValues("[{\"text\":\"Hello\\n\",\"bold\":true},"
                        + "{\"text\":\"World\",\"children\":[{\"text\":\"nested\"}]},"
                        + "{\"rows\":[[{\"text\":3}]]}]"));
    }

    @Test
    public void testContainerAndNullTextSkipped() throws Exception {
        //a text field holding an object or array is skipped whole, nulls add nothing
        assertEquals(Collections.singletonList("after"), GeoGebraJson.textValues(
                "[{\"text\":{\"text\":\"inner\"}},{\"text\":[{\"text\":\"inner\"}]},"
                        + "{\"text\":null},{\"text\":\"after\"}]"));
    }

    @Test
    public void testMalformedContentThrows() {
        assertThrows(IOException.class, () -> GeoGebraJson.textValues("[{\"text\":\"x\"}"));
    }
}
