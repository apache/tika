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
package org.apache.tika.parser.html;

/**
 * HTML mapper used to make incoming HTML documents easier to handle by
 * Tika clients. The {@link HtmlParser} looks up an optional HTML mapper from
 * the parse context and uses it to map parsed HTML to "safe" XHTML. A client
 * that wants to customize this mapping can place a custom HtmlMapper instance
 * into the parse context.
 *
 * @since Apache Tika 0.6
 */
public interface HtmlMapper {

    /**
     * Maps HTML element names to Tika's XHTML dialect. If the given element
     * is not part of that dialect, this method returns <code>null</code> and
     * the element is dropped but the content inside it is still processed.
     * "Safe" here means valid in the output dialect; this is not a browser
     * sanitizer, and attribute values are passed through unchanged. See
     * the {@link #isDiscardElement(String)} method for a way to discard
     * the entire contents of an element.
     *
     * @param name HTML element name (upper case)
     * @return XHTML element name (lower case), or
     * <code>null</code> if the element is not in the output dialect
     */
    String mapSafeElement(String name);

    /**
     * Checks whether all content within the given HTML element should be
     * discarded instead of including it in the parse output.
     *
     * @param name HTML element name (upper case)
     * @return <code>true</code> if content inside the named element
     * should be ignored, <code>false</code> otherwise
     */
    boolean isDiscardElement(String name);


    /**
     * Maps HTML attribute names to Tika's XHTML dialect. If the given
     * attribute is not part of that dialect for this element, this method
     * returns <code>null</code>, and the attribute is dropped. Attribute
     * values are not inspected. This method assumes that the element name
     * is valid and normalised.
     *
     * @param elementName   HTML element name (lower case)
     * @param attributeName HTML attribute name (lower case)
     * @return XHTML attribute name (lower case), or
     * <code>null</code> if the attribute is not in the output dialect
     */
    String mapSafeAttribute(String elementName, String attributeName);

}
