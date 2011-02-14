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
 * Evaluation state of a <code>...//...</code> XPath expression. Applies the
 * contained evaluation state to the current element and all its descendants.
 */
public class SubtreeMatcher extends Matcher {

    private final Matcher then;

    public SubtreeMatcher(Matcher then) {
        this.then = then;
    }

    @Override
    public Matcher descend(String namespace, String name) {
        Matcher next = then.descend(namespace, name);
        if (next == FAIL || next == then) {
            return this;
        } else {
            return new CompositeMatcher(next, this);
        }
    }

    @Override
    public boolean matchesElement() {
        return then.matchesElement();
    }

    @Override
    public boolean matchesAttribute(String namespace, String name) {
        return then.matchesAttribute(namespace, name);
    }

    @Override
    public boolean matchesText() {
        return then.matchesText();
    }

}
