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

public class Link {

    private final String type;

    private final String uri;

    private final String title;

    private final String text;

    private final String rel;

    public Link(String type, String uri, String title, String text) {
        this.type = type;
        this.uri = uri;
        this.title = title;
        this.text = text;
        this.rel = "";
    }

    public Link(String type, String uri, String title, String text, String rel) {
        this.type = type;
        this.uri = uri;
        this.title = title;
        this.text = text;
        this.rel = rel;
    }

    public boolean isAnchor() {
        return "a".equals(type);
    }

    public boolean isImage() {
        return "img".equals(type);
    }

    public String getType() {
        return type;
    }

    public String getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getRel() {
      return rel;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (isImage()) {
            builder.append("<img src=\"");
            builder.append(uri);
            if (title != null && title.length() > 0) {
                builder.append("\" title=\"");
                builder.append(title);
            }
            if (text != null && text.length() > 0) {
                builder.append("\" alt=\"");
                builder.append(text);
            }
            builder.append("\"/>");
        } else {
            builder.append("<");
            builder.append(type);
            builder.append(" href=\"");
            builder.append(uri);
            if (title != null && title.length() > 0) {
                builder.append("\" title=\"");
                builder.append(title);
            }
            if (rel != null && rel.length() > 0) {
                builder.append("\" rel=\"");
                builder.append(rel);
            }
            builder.append("\">");
            builder.append(text);
            builder.append("</");
            builder.append(type);
            builder.append(">");
        }
        return builder.toString();
    }

}
