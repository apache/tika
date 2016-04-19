package org.apache.tika.util;

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

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class XMLDOMUtil {

    /**
     * This grabs the attributes from a dom node and overwrites those values with those
     * specified by the overwrite map.
     *
     * @param node node for building
     * @param overwrite map of attributes to overwrite
     * @return map of attributes
     */
    public static Map<String, String> mapifyAttrs(Node node, Map<String, String> overwrite) {
        Map<String, String> map = new HashMap<String, String>();
        NamedNodeMap nnMap = node.getAttributes();
        for (int i = 0; i < nnMap.getLength(); i++) {
            Node attr = nnMap.item(i);
            map.put(attr.getNodeName(), attr.getNodeValue());
        }
        if (overwrite != null) {
            for (Map.Entry<String, String> e : overwrite.entrySet()) {
                map.put(e.getKey(), e.getValue());
            }
        }
        return map;
    }


    /**
     * Get an int value.  Try the runtime attributes first and then back off to
     * the document element.  Throw a RuntimeException if the attribute is not
     * found or if the value is not parseable as an int.
     *
     * @param attrName attribute name to find
     * @param runtimeAttributes runtime attributes
     * @param docElement correct element that should have specified attribute
     * @return specified int value
     */
    public static int getInt(String attrName, Map<String, String> runtimeAttributes, Node docElement) {
        String stringValue = getStringValue(attrName, runtimeAttributes, docElement);
        if (stringValue != null) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                //swallow
            }
        }
        throw new RuntimeException("Need to specify a parseable int value in -- "
                +attrName+" -- in commandline or in config file!");
    }


    /**
     * Get a long value.  Try the runtime attributes first and then back off to
     * the document element.  Throw a RuntimeException if the attribute is not
     * found or if the value is not parseable as a long.
     *
     * @param attrName attribute name to find
     * @param runtimeAttributes runtime attributes
     * @param docElement correct element that should have specified attribute
     * @return specified long value
     */
    public static long getLong(String attrName, Map<String, String> runtimeAttributes, Node docElement) {
        String stringValue = getStringValue(attrName, runtimeAttributes, docElement);
        if (stringValue != null) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                //swallow
            }
        }
        throw new RuntimeException("Need to specify a \"long\" value in -- "
                +attrName+" -- in commandline or in config file!");
    }

    private static String getStringValue(String attrName, Map<String, String> runtimeAttributes, Node docElement) {
        String stringValue = runtimeAttributes.get(attrName);
        if (stringValue == null) {
            Node staleNode = docElement.getAttributes().getNamedItem(attrName);
            if (staleNode != null) {
                stringValue = staleNode.getNodeValue();
            }
        }
        return stringValue;
    }
}
