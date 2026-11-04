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
package org.apache.tika.mime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.tika.utils.XMLReaderUtils;

/**
 * Rules in tika-mimetypes.xml that load fine but can never match as written.
 * Checks the raw XML: once decoded, a mistyped value is indistinguishable from an intended one.
 */
public class MimeTypesLintTest {

    @Test
    public void testDefaultMimeTypes() throws Exception {
        try (InputStream is = MimeTypesReader.class.getResourceAsStream("tika-mimetypes.xml")) {
            List<String> problems = lint(is);
            assertTrue(problems.isEmpty(), String.join("\n", problems));
        }
    }

    @Test
    public void testLintCatchesEachProblem() throws Exception {
        String xml = "<mime-info>" +
                "<mime-type type=\"application/x-a\"><magic>" +
                "<match value=\"0x310D0A13030\" type=\"string\" offset=\"0\"/>" +
                "<match value=\"0x31zz\" type=\"string\" offset=\"0\"/>" +
                "<match value=\"(?i)&lt;html[\\s&gt;]\" type=\"regex\" offset=\"0\"/>" +
                "<match value=\"a\\db\" type=\"string\" offset=\"0\"/>" +
                "<match value=\"0x0102\" mask=\"0xFF\" type=\"string\" offset=\"0\"/>" +
                "<match value=\"x\" type=\"string\" offset=\"8:4\"/>" +
                "<match value=\"x\" type=\"string\" offset=\"0:65536\"/>" +
                "</magic><sub-class-of type=\"application/x-undefined\"/></mime-type>" +
                "<mime-type type=\"application/x-b\">" +
                "<alias type=\"application/x-b-alias\"/><magic>" +
                "<match value=\"(?i)&lt;html[\\\\s&gt;]\" type=\"regex\" offset=\"0\"/>" +
                "<match value=\"\\x00\\n\\\\a\\101\" type=\"string\" offset=\"0\"/>" +
                "</magic><sub-class-of type=\"application/x-a\"/></mime-type>" +
                "<mime-type type=\"application/x-c\"><sub-class-of type=\"application/x-b-alias\"/>" +
                "</mime-type></mime-info>";
        List<String> problems = lint(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        assertEquals(8, problems.size(), String.join("\n", problems));
        for (String p : problems) {
            assertTrue(p.startsWith("application/x-a "), p);
        }
    }

    static List<String> lint(InputStream is) throws Exception {
        Document doc = XMLReaderUtils.buildDOM(is);
        NodeList types = doc.getElementsByTagName("mime-type");
        Set<String> defined = new HashSet<>();
        for (int i = 0; i < types.getLength(); i++) {
            Element type = (Element) types.item(i);
            defined.add(type.getAttribute("type").toLowerCase(Locale.ROOT));
            for (Element alias : children(type, "alias")) {
                defined.add(alias.getAttribute("type").toLowerCase(Locale.ROOT));
            }
        }
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < types.getLength(); i++) {
            Element type = (Element) types.item(i);
            String name = type.getAttribute("type");
            for (Element parent : children(type, "sub-class-of")) {
                String p = parent.getAttribute("type");
                if (!defined.contains(p.toLowerCase(Locale.ROOT))) {
                    problems.add(name + " sub-class-of undefined type " + p);
                }
            }
            NodeList matches = type.getElementsByTagName("match");
            for (int j = 0; j < matches.getLength(); j++) {
                lintMatch(name, (Element) matches.item(j), problems);
            }
        }
        return problems;
    }

    private static void lintMatch(String name, Element match, List<String> problems) {
        String kind = match.hasAttribute("type") ? match.getAttribute("type") : "string";
        String value = match.getAttribute("value");
        String mask = match.getAttribute("mask");
        String where = name + " match '" + value + "': ";
        boolean bytewise = kind.startsWith("string") || kind.equals("regex");
        if (value.startsWith("0x")) {
            String hex = value.substring(2);
            if (!hex.matches("[0-9A-Fa-f]*")) {
                problems.add(where + "non-hex digit");
            } else if (bytewise && hex.length() % 2 != 0) {
                // decoding takes digit pairs and drops the last one
                problems.add(where + "odd number of hex digits");
            }
            if (mask.startsWith("0x") && mask.length() != value.length()) {
                problems.add(where + "mask and value differ in length");
            }
        } else {
            for (int k = 0; k + 1 < value.length(); k++) {
                if (value.charAt(k) != '\\') {
                    continue;
                }
                char c = value.charAt(++k);
                if (c == '\\' || c == 'x' || c == 'n' || c == 'r' || c == 't' || Character.isDigit(c)) {
                    continue;
                }
                // the decoder keeps only c: "\s" is "s", "\." is "."; regex escapes need "\\"
                if (kind.equals("regex") || Character.isLetter(c)) {
                    problems.add(where + "escape \\" + c + " decodes to a bare '" + c + "'");
                }
            }
        }
        String offset = match.getAttribute("offset");
        if (!offset.isEmpty()) {
            String[] range = offset.split(":");
            int start = Integer.parseInt(range[0].trim());
            int end = range.length > 1 ? Integer.parseInt(range[1].trim()) : start;
            if (end < start) {
                problems.add(where + "offset range ends before it starts");
            } else if (end >= 64 * 1024) {
                problems.add(where + "offset beyond the bytes MimeTypes reads");
            }
        }
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add((Element) nodes.item(i));
        }
        return out;
    }
}
