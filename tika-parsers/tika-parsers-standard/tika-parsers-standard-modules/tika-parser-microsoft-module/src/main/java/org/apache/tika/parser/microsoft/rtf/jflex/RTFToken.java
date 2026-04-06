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
package org.apache.tika.parser.microsoft.rtf.jflex;

/**
 * A single token produced by the RTF tokenizer.
 * <p>
 * Mutable and reused by the tokenizer to avoid allocation in the hot loop.
 * Consumers must copy any data they need before requesting the next token.
 * <p>
 * For TEXT and CONTROL_SYMBOL tokens (single character), use {@link #getChar()}
 * to avoid String allocation. For CONTROL_WORD tokens, use {@link #getName()}.
 */
public class RTFToken {

    private RTFTokenType type;
    private String name;
    private char ch;
    private int parameter;
    private boolean hasParameter;

    public void reset(RTFTokenType type) {
        this.type = type;
        this.name = null;
        this.ch = 0;
        this.parameter = -1;
        this.hasParameter = false;
    }

    public void set(RTFTokenType type, String name, int parameter, boolean hasParameter) {
        this.type = type;
        this.name = name;
        this.ch = 0;
        this.parameter = parameter;
        this.hasParameter = hasParameter;
    }

    public void setChar(RTFTokenType type, char ch) {
        this.type = type;
        this.name = null;
        this.ch = ch;
        this.parameter = -1;
        this.hasParameter = false;
    }

    public RTFTokenType getType() {
        return type;
    }

    /** For CONTROL_WORD tokens: the control word name. */
    public String getName() {
        return name;
    }

    /**
     * For TEXT and CONTROL_SYMBOL tokens: the single character, without
     * allocating a String.
     */
    public char getChar() {
        return ch;
    }

    public int getParameter() {
        return parameter;
    }

    public boolean hasParameter() {
        return hasParameter;
    }

    public int getHexValue() {
        return parameter;
    }

    @Override
    public String toString() {
        switch (type) {
            case GROUP_OPEN:
                return "{";
            case GROUP_CLOSE:
                return "}";
            case CONTROL_WORD:
                return "\\" + name + (hasParameter ? String.valueOf(parameter) : "");
            case CONTROL_SYMBOL:
                return "\\" + ch;
            case HEX_ESCAPE:
                return String.format(java.util.Locale.ROOT, "\\'%02x", parameter);
            case UNICODE_ESCAPE:
                return "\\u" + parameter;
            case TEXT:
                return "TEXT[" + ch + "]";
            case BIN:
                return "\\bin" + parameter;
            case CRLF:
                return "CRLF";
            case EOF:
                return "EOF";
            default:
                return type.name();
        }
    }
}
