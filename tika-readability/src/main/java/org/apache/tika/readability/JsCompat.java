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
package org.apache.tika.readability;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript string semantics the port depends on. JS {@code \s} and {@code trim()} include
 * NBSP and the Unicode space separators; Java's do not, and Java's {@code $} also matches before
 * a final line terminator. Regexes in this package use {@link #S}/{@link #NS} and {@code \z}.
 */
final class JsCompat {

    static final String WS_CHARS =
            "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";
    /** JS {@code \s}. */
    static final String S = "[" + WS_CHARS + "]";
    /** JS {@code \S}. */
    static final String NS = "[^" + WS_CHARS + "]";

    static final Pattern WS_RUN = Pattern.compile(S + "+");
    private static final Pattern WS_ANY = Pattern.compile(S);
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

    private JsCompat() {
    }

    static boolean isWs(char c) {
        switch (c) {
            case '\t':
            case '\n':
            case '\u000B':
            case '\f':
            case '\r':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case '　':
            case '﻿':
                return true;
            default:
                return c >= ' ' && c <= ' ';
        }
    }

    static String trim(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isWs(s.charAt(start))) {
            start++;
        }
        while (end > start && isWs(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    static boolean truthy(String s) {
        return s != null && !s.isEmpty();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    static String removeWhitespace(String s) {
        return WS_ANY.matcher(s).replaceAll("");
    }

    /** JS {@code str.split(regex).length}: keeps trailing empty strings. */
    static int splitLength(String s, Pattern p) {
        return p.split(s, -1).length;
    }

    /** JS {@code parseInt(s, 10)}; NaN when there is no leading integer. */
    static double parseInt(String s) {
        String t = trim(s);
        int i = 0;
        boolean neg = false;
        if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
            neg = t.charAt(i) == '-';
            i++;
        }
        int startDigits = i;
        double v = 0;
        while (i < t.length() && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
            v = v * 10 + (t.charAt(i) - '0');
            i++;
        }
        if (i == startDigits) {
            return Double.NaN;
        }
        return neg ? -v : v;
    }

    static boolean truthy(double d) {
        return d != 0 && !Double.isNaN(d);
    }

    /** Approximates WHATWG {@code new URL(str)} succeeding. */
    static boolean isAbsoluteUrl(String str) {
        String s = stripUrl(str);
        if (!SCHEME.matcher(s).find()) {
            return false;
        }
        int colon = s.indexOf(':');
        String scheme = lower(s.substring(0, colon));
        if (isSpecial(scheme) && !"file".equals(scheme)) {
            String rest = s.substring(colon + 1).replaceFirst("^[/\\\\]*", "");
            return !rest.isEmpty() && "/?#".indexOf(rest.charAt(0)) < 0;
        }
        return true;
    }

    /** RFC 3986 appendix B. */
    private static final Pattern URL_PARTS =
            Pattern.compile("^(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?", Pattern.DOTALL);
    private static final Pattern VALID_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*\\z");
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^/([A-Za-z])[|:](/|\\z)");

    /** Components of a reference; null means absent, as distinct from empty. */
    private record Url(String scheme, String authority, String path, String query, String fragment) {
    }

    private static Url split(String s) {
        Matcher m = URL_PARTS.matcher(s);
        if (!m.find()) {
            return null;
        }
        String scheme = m.group(1);
        if (scheme != null && !VALID_SCHEME.matcher(scheme).find()) {
            // not a scheme after all (e.g. "​https://..."): the whole thing is a relative path
            Matcher rel = URL_PARTS.matcher("./" + s);
            rel.find();
            return new Url(null, null, rel.group(3).substring(2), rel.group(4), rel.group(5));
        }
        return new Url(scheme == null ? null : lower(scheme), m.group(2), m.group(3), m.group(4), m.group(5));
    }

    /**
     * Approximates WHATWG {@code new URL(rel, base).href}: RFC 3986 resolution, keeping empty
     * path segments, and WHATWG's percent-encoding and host/port/path normalization. Returns
     * {@code null} where WHATWG would throw.
     */
    static String resolveUrl(String base, String rel) {
        try {
            String r = stripUrl(rel);
            Url ref = split(r);
            if (ref == null) {
                return null;
            }
            if (ref.scheme() != null && !isSpecial(ref.scheme())) {
                return ref.scheme() + r.substring(ref.scheme().length());
            }
            if (ref.scheme() != null) {
                ref = split(r.replace('\\', '/'));
                return serialize(new Url(ref.scheme(), ref.authority(), removeDotSegments(ref.path()), ref.query(),
                        ref.fragment()));
            }
            if (base == null || base.isEmpty()) {
                return null;
            }
            Url b = split(stripUrl(base));
            if (b == null || b.scheme() == null || !isSpecial(b.scheme())) {
                return null;
            }
            ref = split(r.replace('\\', '/'));
            Url t;
            if (ref.authority() != null) {
                t = new Url(b.scheme(), ref.authority(), removeDotSegments(ref.path()), ref.query(), ref.fragment());
            } else if (ref.path().isEmpty()) {
                t = new Url(b.scheme(), b.authority(), b.path(), ref.query() != null ? ref.query() : b.query(),
                        ref.fragment());
            } else if (ref.path().startsWith("/")) {
                t = new Url(b.scheme(), b.authority(), removeDotSegments(ref.path()), ref.query(), ref.fragment());
            } else {
                t = new Url(b.scheme(), b.authority(), removeDotSegments(merge(b, ref.path())), ref.query(),
                        ref.fragment());
            }
            return serialize(t);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String merge(Url base, String refPath) {
        if (base.authority() != null && base.path().isEmpty()) {
            return "/" + refPath;
        }
        int slash = base.path().lastIndexOf('/');
        return base.path().substring(0, slash + 1) + refPath;
    }

    /** RFC 3986 5.2.4; unlike java.net.URI it keeps empty segments ("a//b"). */
    private static String removeDotSegments(String path) {
        String in = path;
        StringBuilder out = new StringBuilder();
        while (!in.isEmpty()) {
            if (in.startsWith("../")) {
                in = in.substring(3);
            } else if (in.startsWith("./")) {
                in = in.substring(2);
            } else if (in.startsWith("/./")) {
                in = in.substring(2);
            } else if (in.equals("/.")) {
                in = "/";
            } else if (in.startsWith("/../")) {
                in = in.substring(3);
                popSegment(out);
            } else if (in.equals("/..")) {
                in = "/";
                popSegment(out);
            } else if (in.equals(".") || in.equals("..")) {
                in = "";
            } else {
                int next = in.indexOf('/', in.startsWith("/") ? 1 : 0);
                if (next < 0) {
                    next = in.length();
                }
                out.append(in, 0, next);
                in = in.substring(next);
            }
        }
        return out.toString();
    }

    private static void popSegment(StringBuilder out) {
        int slash = out.lastIndexOf("/");
        out.setLength(Math.max(slash, 0));
    }

    private static String serialize(Url u) {
        StringBuilder sb = new StringBuilder();
        sb.append(u.scheme()).append(':');
        String path = encode(u.path(), PATH_ENCODE);
        if (u.authority() != null) {
            sb.append("//").append(authority(u.scheme(), u.authority()));
            if (path.isEmpty()) {
                path = "/";
            }
        } else if ("file".equals(u.scheme())) {
            sb.append("//");
        }
        if ("file".equals(u.scheme())) {
            Matcher drive = WINDOWS_DRIVE.matcher(path);
            if (drive.find()) {
                path = "/" + drive.group(1) + ":" + path.substring(3);
            }
        }
        sb.append(path);
        if (u.query() != null) {
            sb.append('?').append(encode(u.query(), QUERY_ENCODE));
        }
        if (u.fragment() != null) {
            sb.append('#').append(encode(u.fragment(), FRAGMENT_ENCODE));
        }
        return sb.toString();
    }

    private static String authority(String scheme, String authority) {
        String userInfo = "";
        String hostPort = authority;
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            userInfo = authority.substring(0, at + 1);
            hostPort = authority.substring(at + 1);
        }
        String host = hostPort;
        String port = null;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && hostPort.indexOf(']', colon) < 0) {
            host = hostPort.substring(0, colon);
            port = hostPort.substring(colon + 1);
        }
        if (host.isEmpty() && !"file".equals(scheme)) {
            throw new IllegalArgumentException("empty host");
        }
        StringBuilder sb = new StringBuilder(userInfo).append(lower(host));
        if (port != null && !port.isEmpty()) {
            if (!port.chars().allMatch(c -> c >= '0' && c <= '9')) {
                throw new IllegalArgumentException("bad port");
            }
            int p = Integer.parseInt(port.length() > 5 ? "99999" : port);
            if (p > 65535) {
                throw new IllegalArgumentException("bad port");
            }
            if (!isDefaultPort(scheme, p)) {
                sb.append(':').append(p);
            }
        }
        return sb.toString();
    }

    private static boolean isSpecial(String scheme) {
        switch (scheme) {
            case "http":
            case "https":
            case "ftp":
            case "ws":
            case "wss":
            case "file":
                return true;
            default:
                return false;
        }
    }

    /** WHATWG strips leading/trailing C0-or-space and every tab/newline. */
    private static String stripUrl(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && s.charAt(end - 1) <= ' ') {
            end--;
        }
        return s.substring(start, end).replace("\t", "").replace("\n", "").replace("\r", "");
    }

    // WHATWG percent-encode sets (beyond C0 controls and non-ASCII, always encoded)
    private static final String PATH_ENCODE = " \"#<>?`{}";
    private static final String QUERY_ENCODE = " \"#<>'";
    private static final String FRAGMENT_ENCODE = " \"<>`";

    private static String encode(String s, String set) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0x20 && c < 0x7F && set.indexOf(c) < 0) {
                sb.append(c);
                continue;
            }
            int cp = Character.codePointAt(s, i);
            if (Character.charCount(cp) == 2) {
                i++;
            }
            for (byte by : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                sb.append('%').append(String.format(Locale.ROOT, "%02X", by & 0xFF));
            }
        }
        return sb.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        switch (scheme) {
            case "http":
            case "ws":
                return port == 80;
            case "https":
            case "wss":
                return port == 443;
            case "ftp":
                return port == 21;
            default:
                return false;
        }
    }
}
