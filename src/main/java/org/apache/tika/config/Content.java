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
package org.apache.tika.config;

import org.jdom.Element;

/**
 * Content object, used to configure and store data populated from XPATH, Regex,
 * Document fulltext, document metadata etc.
 */
public class Content {

    private final String name;

    private final String textSelect;

    private final String xPathSelect;

    private final String regexSelect;

    private String value;

    private String[] values;

    public Content(Element element) {
        name = element.getAttributeValue("name");
        xPathSelect = element.getAttributeValue("xpathSelect");
        textSelect = element.getAttributeValue("textSelect");
        regexSelect = element.getChildTextTrim("regexSelect");
    }

    public String getName() {
        return name;
    }

    public String getRegexSelect() {
        return regexSelect;
    }

    public String getTextSelect() {
        return textSelect;
    }

    public String getXPathSelect() {
        return xPathSelect;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }

}
