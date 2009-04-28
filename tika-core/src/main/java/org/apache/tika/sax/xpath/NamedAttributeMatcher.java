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
package org.apache.tika.sax.xpath;

/**
 * Final evaluation state of a <code>.../@name</code> XPath expression.
 * Matches the named attributes of the current element.
 */
public class NamedAttributeMatcher extends Matcher {

    private final String namespace;

    private final String name;

    public NamedAttributeMatcher(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public boolean matchesAttribute(String namespace, String name) {
        return equals(namespace, this.namespace) && name.equals(this.name);
    }

    private static boolean equals(String a, String b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

}
