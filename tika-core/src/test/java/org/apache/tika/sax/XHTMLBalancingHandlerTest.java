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
package org.apache.tika.sax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

public class XHTMLBalancingHandlerTest {

    private static AttributesImpl noAtts() {
        return new AttributesImpl();
    }

    @Test
    public void happyPathIsPassthrough() throws Exception {
        ToXMLContentHandler out = new ToXMLContentHandler();
        XHTMLBalancingHandler balancer = new XHTMLBalancingHandler(out);

        balancer.startDocument();
        balancer.startElement("", "p", "p", noAtts());
        balancer.characters("hello".toCharArray(), 0, 5);
        balancer.endElement("", "p", "p");
        balancer.endDocument();

        assertEquals(0, balancer.openElementCount());
        // No drain needed -- stack should already be empty.
        balancer.drainOpenElements();
        // ToXMLContentHandler.toString() returns the serialized form.
        String xml = out.toString();
        assertEquals(true, xml.contains("<p>hello</p>"), xml);
    }

    @Test
    public void drainClosesElementsInReverseOpenOrder() throws Exception {
        ToXMLContentHandler out = new ToXMLContentHandler();
        XHTMLBalancingHandler balancer = new XHTMLBalancingHandler(out);

        balancer.startDocument();
        balancer.startElement("", "div", "div", noAtts());
        balancer.startElement("", "p", "p", noAtts());
        balancer.startElement("", "span", "span", noAtts());
        balancer.characters("oops".toCharArray(), 0, 4);

        // Simulate exception mid-element: caller drains.
        assertEquals(3, balancer.openElementCount());
        balancer.drainOpenElements();
        assertEquals(0, balancer.openElementCount());

        balancer.endDocument();

        String xml = out.toString();
        // Expect </span></p></div> in that order.
        int spanIdx = xml.indexOf("</span>");
        int pIdx = xml.indexOf("</p>");
        int divIdx = xml.indexOf("</div>");
        assertEquals(true, spanIdx >= 0 && pIdx > spanIdx && divIdx > pIdx,
                "expected </span></p></div> order, got: " + xml);
    }

    @Test
    public void drainEmitsMatchingUriAndQName() throws Exception {
        // Verifies the Copilot review point: endElement must carry the same
        // (uri, localName, qName) tuple as the matching startElement.
        ToXMLContentHandler out = new ToXMLContentHandler();
        XHTMLBalancingHandler balancer = new XHTMLBalancingHandler(out);

        balancer.startDocument();
        balancer.startPrefixMapping("h", "http://example.com/ns");
        balancer.startElement("http://example.com/ns", "wrap", "h:wrap", noAtts());
        // Emit content so the serializer can't collapse to a self-closing tag,
        // forcing the close form to be explicit -- proves drainOpen used the
        // matching qName ("h:wrap") rather than just the local name.
        balancer.characters("x".toCharArray(), 0, 1);
        balancer.drainOpenElements();
        balancer.endPrefixMapping("h");
        balancer.endDocument();

        String xml = out.toString();
        assertEquals(true, xml.contains("</h:wrap>"),
                "expected qualified close </h:wrap>, got: " + xml);
    }

    @Test
    public void drainIsIdempotent() throws Exception {
        ToXMLContentHandler out = new ToXMLContentHandler();
        XHTMLBalancingHandler balancer = new XHTMLBalancingHandler(out);

        balancer.startDocument();
        balancer.startElement("", "p", "p", noAtts());
        balancer.drainOpenElements();
        balancer.drainOpenElements();  // second call: no-op
        balancer.endDocument();
        assertEquals(0, balancer.openElementCount());
    }

    @Test
    public void downstreamValidatorStillCatchesMismatchedEndElement() throws Exception {
        // Balancer must NOT silently fix bad happy-path sequences -- the
        // StrictXHTMLValidator wrapping the real handler must still see (and
        // reject) excess endElement events.
        ToXMLContentHandler out = new ToXMLContentHandler();
        StrictXHTMLValidator validator = new StrictXHTMLValidator(out);
        XHTMLBalancingHandler balancer = new XHTMLBalancingHandler(validator);

        balancer.startDocument();
        balancer.startElement("", "p", "p", noAtts());
        balancer.endElement("", "p", "p");
        // Extra endElement: stack is empty so balancer pops nothing, but the
        // event still flows downstream to the validator, which must throw.
        assertThrows(org.xml.sax.SAXException.class,
                () -> balancer.endElement("", "p", "p"));
    }
}
