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
 * Composite XPath evaluation state. Used when XPath evaluation results
 * in two or more branches of independent evaluation states.
 */
public class CompositeMatcher extends Matcher {

    private final Matcher a;

    private final Matcher b;

    public CompositeMatcher(Matcher a, Matcher b) {
        this.a = a;
        this.b = b;
    }

    public Matcher descend(String namespace, String name) {
        Matcher a = this.a.descend(namespace, name);
        Matcher b = this.b.descend(namespace, name);
        if (a == FAIL) {
            return b;
        } else if (b == FAIL) {
            return a;
        } else if (this.a == a && this.b == b) {
            return this;
        } else {
            return new CompositeMatcher(a, b);
        }
    }

    public boolean matchesElement() {
        return a.matchesElement() || b.matchesElement();
    }

    public boolean matchesAttribute(String namespace, String name) {
        return a.matchesAttribute(namespace, name)
            || b.matchesAttribute(namespace, name);
    }

    public boolean matchesText() {
        return a.matchesText() || b.matchesText();
    }

}
