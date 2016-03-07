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

class LinkBuilder {

    private final String type;

    private String uri = "";

    private String title = "";

    private String rel = "";

    private final StringBuilder text = new StringBuilder();

    public LinkBuilder(String type) {
        this.type = type;
    }

    public void setURI(String uri) {
        if (uri != null) {
            this.uri = uri;
        } else {
            this.uri = "";
        }
    }

    public void setTitle(String title) {
        if (title != null) {
            this.title = title;
        } else {
            this.title = "";
        }
    }

    public void setRel(String rel) {
        if (rel != null) {
            this.rel = rel;
        } else {
            this.rel = "";
        }
    }

    public void characters(char[] ch, int offset, int length) {
        text.append(ch, offset, length);
    }

    public Link getLink() {
        return getLink(false);
    }
    
    public Link getLink(boolean collapseWhitespace) {
        String anchor = text.toString();
        
        if (collapseWhitespace) {
            anchor = anchor.replaceAll("\\s+", " ").trim();
        }
        
        return new Link(type, uri, title, anchor, rel);
    }

}
