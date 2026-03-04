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
package org.apache.tika.detect;

import java.nio.charset.Charset;

/**
 * Carries the outcome of {@link WideUnicodeDetector} through the
 * {@link org.apache.tika.parser.ParseContext} so that downstream detectors
 * can defer without re-examining the bytes.
 *
 * <p>Presence of this object in the {@code ParseContext} means
 * {@link WideUnicodeDetector} has already claimed the stream.
 * Downstream detectors (e.g. an ML-based detector whose model was not
 * trained on null-heavy UTF-16/32 data) should return an empty result
 * when they find this signal, regardless of its {@link Kind}.</p>
 *
 * <h3>Kinds</h3>
 * <ul>
 *   <li>{@link Kind#VALID} — a well-formed UTF-16 or UTF-32 stream was
 *       detected; {@link #getCharset()} returns the encoding.</li>
 *   <li>{@link Kind#ILLEGAL_SURROGATES} — the byte stream has a clear
 *       wide-Unicode null-column pattern but contains illegal surrogate
 *       sequences, making it undecodable as UTF-16.
 *       {@link #getCharset()} returns {@code null}.</li>
 * </ul>
 *
 * @since Apache Tika 4.0
 */
public final class WideUnicodeSignal {

    public enum Kind {
        /** A structurally valid UTF-16 or UTF-32 stream. */
        VALID,
        /**
         * The stream looks like UTF-16 (high null-column density) but contains
         * illegal surrogate sequences: a lone low surrogate, or a high surrogate
         * not followed by a low surrogate.
         */
        ILLEGAL_SURROGATES
    }

    private final Kind kind;
    private final Charset charset;

    private WideUnicodeSignal(Kind kind, Charset charset) {
        this.kind = kind;
        this.charset = charset;
    }

    /** Factory for a successfully detected wide-Unicode encoding. */
    public static WideUnicodeSignal valid(Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("charset must not be null for a VALID signal");
        }
        return new WideUnicodeSignal(Kind.VALID, charset);
    }

    /** Factory for a stream that has wide-Unicode structure but illegal surrogates. */
    public static WideUnicodeSignal illegalSurrogates() {
        return new WideUnicodeSignal(Kind.ILLEGAL_SURROGATES, null);
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * The detected charset; {@code null} when kind is
     * {@link Kind#ILLEGAL_SURROGATES}.
     */
    public Charset getCharset() {
        return charset;
    }

    public boolean isIllegalSurrogates() {
        return kind == Kind.ILLEGAL_SURROGATES;
    }

    @Override
    public String toString() {
        return kind + (charset != null ? "(" + charset.name() + ")" : "");
    }
}
