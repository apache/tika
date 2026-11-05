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
package org.apache.tika.ml.chardetect.tools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds charset-detection dev/test data from per-language Wikipedia text.
 *
 * <p>Unlike {@link BuildCharsetTrainingData}, the charset set here is independent of the
 * trained class list: every charset listed for a language in the language/charset table is
 * generated, so untrained charsets show up in eval as an out-of-scope tier.
 *
 * <p>Output, per split: {@code <out>/<split>/<charset>.bin.gz} (gzipped
 * {@code [uint16-BE length][bytes]} chunks, readable by {@link EvalCharsetDetectors}) and a
 * parallel {@code <charset>.meta.jsonl.gz} with one line per chunk: language, article id,
 * variant, substitution flag, and the number of characters dropped as unencodable (never letters).
 *
 * <p>The label is the encoding the bytes were written in. Correctness is decided by the scorer
 * as "the predicted charset decodes these bytes to the same string", so no fold table is
 * needed here.
 *
 * <pre>
 * BuildCharsetEvalData --wiki-dir DIR --lang-charsets FILE.tsv --output-dir DIR
 *     [--per-cell 500] [--chunk-bytes 2048] [--seed 20261102] [--context-lang eng]
 * </pre>
 *
 * <p>The lang-charsets TSV has columns {@code lang, charset, kind}, where kind is
 * {@code native} or {@code subst} (encode after applying that language's de-facto substitution
 * map, e.g. Persian ی to ي for windows-1256).
 */
public class BuildCharsetEvalData {

    static final String[] SPLITS = {"dev", "test"};
    static final String[] VARIANTS = {"prose", "html", "short", "nearascii-mismatch"};
    static final int[] NEAR_ASCII_WORDS = {1, 2, 5, 20};

    /** De-facto substitutions real legacy text used where the charset lacks a letter. */
    static final Map<String, Map<Integer, Integer>> SUBST = CharsetTextEncoding.SUBST;

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        Path wikiDir = null, langCharsets = null, outDir = null;
        int perCell = 500, chunkBytes = 2048;
        long seed = 20261102L;
        String contextLang = "eng";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--wiki-dir":
                    wikiDir = Path.of(args[++i]);
                    break;
                case "--lang-charsets":
                    langCharsets = Path.of(args[++i]);
                    break;
                case "--output-dir":
                    outDir = Path.of(args[++i]);
                    break;
                case "--per-cell":
                    perCell = Integer.parseInt(args[++i]);
                    break;
                case "--chunk-bytes":
                    chunkBytes = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--context-lang":
                    contextLang = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown arg " + args[i]);
            }
        }
        if (wikiDir == null || langCharsets == null || outDir == null) {
            System.err.println("usage: BuildCharsetEvalData --wiki-dir DIR --lang-charsets TSV --output-dir DIR");
            System.exit(1);
        }
        final Path out = outDir;
        Map<String, List<String[]>> table = readTable(langCharsets);
        Map<String, List<Article>> context = null;
        if (Files.isDirectory(wikiDir.resolve(contextLang))) {
            context = loadArticles(wikiDir.resolve(contextLang).resolve("articles.jsonl.gz"));
        } else {
            System.err.println("no context language '" + contextLang + "'; skipping nearascii-mismatch");
        }
        Map<String, Writers> writers = new TreeMap<>();
        Map<String, int[]> counts = new TreeMap<>();
        try {
            for (Map.Entry<String, List<String[]>> e : table.entrySet()) {
                String lang = e.getKey();
                // script-variant tags (zho-Hant) draw from the base wiki; the drop cap rejects off-script articles
                Path f = wikiDir.resolve(lang.replaceFirst("-.*", "")).resolve("articles.jsonl.gz");
                if (!Files.exists(f)) {
                    System.err.println("no wiki text for " + lang + "; skipping");
                    continue;
                }
                Map<String, List<Article>> bySplit = loadArticles(f);
                for (String[] row : e.getValue()) {
                    Charset cs = Charset.forName(row[1]);
                    boolean subst = "subst".equals(row[2]);
                    for (String split : SPLITS) {
                        List<Article> arts = bySplit.getOrDefault(split, List.of());
                        if (arts.isEmpty()) {
                            continue;
                        }
                        Writers w = writers.computeIfAbsent(split + "/" + cs.name(),
                                k -> Writers.open(out, split, cs.name()));
                        for (String variant : VARIANTS) {
                            if (variant.startsWith("nearascii") && (context == null || lang.equals(contextLang))) {
                                continue;
                            }
                            // HTML in EBCDIC doesn't occur in practice; scoring it rewards artifacts
                            // ("<!DOCTYPE" alone decides IBM500 vs IBM1047)
                            if (variant.equals("html") && !htmlPlausible(cs)) {
                                continue;
                            }
                            Random r = new Random(seed ^ (lang + "|" + cs.name() + "|" + split + "|" + variant).hashCode());
                            int made = 0, tries = 0;
                            while (made < perCell && tries < perCell * 20) {
                                tries++;
                                Article a = arts.get(r.nextInt(arts.size()));
                                int[] k = {0};
                                String text = makeText(variant, a, r, chunkBytes, context == null ? null
                                        : context.getOrDefault(split, List.of()), lang, k);
                                if (text == null) {
                                    continue;
                                }
                                if (subst) {
                                    text = substitute(text, SUBST.get(lang));
                                }
                                int[] dropped = new int[2];
                                byte[] bytes = encode(text, cs, chunkBytes, dropped);
                                // any dropped letter would bias the cell toward text without the hard letters
                                if (bytes == null || dropped[1] > 0 || dropped[0] > 3 + text.length() / 200
                                        || asciiEquivalent(bytes, cs)) {
                                    continue;
                                }
                                w.write(bytes, lang, a.id, variant, subst, dropped[0], k[0]);
                                made++;
                            }
                            counts.computeIfAbsent(split + "\t" + cs.name() + "\t" + lang + "\t" + variant
                                    + (subst ? "\tsubst" : "\tnative"), k -> new int[1])[0] += made;
                        }
                    }
                }
            }
        } finally {
            for (Writers w : writers.values()) {
                w.close();
            }
        }
        try (Writer m = Files.newBufferedWriter(outDir.resolve("counts.tsv"))) {
            m.write("split\tcharset\tlang\tvariant\tkind\tsamples\n");
            for (Map.Entry<String, int[]> c : counts.entrySet()) {
                m.write(c.getKey() + "\t" + c.getValue()[0] + "\n");
            }
        }
        Files.writeString(outDir.resolve("manifest.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("tool", "BuildCharsetEvalData", "seed", seed, "perCell", perCell, "chunkBytes", chunkBytes,
                        "contextLang", contextLang, "wikiDir", wikiDir.toString(),
                        "langCharsets", langCharsets.toString(), "variants", List.of(VARIANTS))));
    }

    /** ASCII-compatible for markup (or a UTF-16/32 form, where HTML does occur). */
    static boolean htmlPlausible(Charset cs) {
        if (cs.name().startsWith("UTF-")) {
            return true;
        }
        byte[] lt = "<".getBytes(cs);
        return lt.length == 1 && lt[0] == '<';
    }

    static String makeText(String variant, Article a, Random r, int chunkBytes, List<Article> ctx, String lang,
                           int[] kOut) {
        switch (variant) {
            case "prose":
                return span(a.text, r, chunkBytes);
            case "short": {
                return span(a.text, r, 5 + r.nextInt(56));
            }
            case "html":
                return html(a, r, chunkBytes);
            case "nearascii-mismatch": {
                if (ctx == null || ctx.isEmpty()) {
                    return null;
                }
                String base = span(ctx.get(r.nextInt(ctx.size())).text, r, chunkBytes);
                List<String> words = nonAsciiWords(a.text);
                if (base == null || words.isEmpty()) {
                    return null;
                }
                int k = NEAR_ASCII_WORDS[r.nextInt(NEAR_ASCII_WORDS.length)];
                kOut[0] = k;
                String[] toks = base.split(" ");
                for (int i = 0; i < k; i++) {
                    toks[r.nextInt(toks.length)] = words.get(r.nextInt(words.size()));
                }
                return String.join(" ", toks);
            }
            default:
                throw new IllegalArgumentException(variant);
        }
    }

    /** A paragraph-aligned span of about {@code approxChars} characters. */
    static String span(String text, Random r, int approxChars) {
        if (text.length() < 20) {
            return null;
        }
        int start = r.nextInt(Math.max(1, text.length() - Math.min(approxChars, text.length() - 1)));
        int nl = text.lastIndexOf('\n', start);
        start = nl < 0 ? 0 : nl + 1;
        int end = Math.min(text.length(), start + approxChars);
        return text.substring(start, end).strip();
    }

    static String html(Article a, Random r, int chunkBytes) {
        String body = span(a.text, r, chunkBytes);
        if (body == null) {
            return null;
        }
        List<String> words = nonAsciiWords(body);
        String alt = words.isEmpty() ? "image" : words.get(r.nextInt(words.size()));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><title>").append(escape(a.title)).append("</title>\n")
                .append("<link rel=\"stylesheet\" href=\"/static/site.css\">\n</head><body>\n")
                .append("<nav><a href=\"/\">Home</a> | <a href=\"/about\">About</a> | <a href=\"/contact\">Contact</a></nav>\n")
                .append("<h1>").append(escape(a.title)).append("</h1>\n");
        for (String para : body.split("\n")) {
            if (!para.isBlank()) {
                sb.append("<p>").append(escape(para)).append("</p>\n");
            }
        }
        sb.append("<img src=\"/img/1.png\" alt=\"").append(escape(alt)).append("\">\n")
                .append("<footer><a href=\"/privacy\">Privacy</a></footer>\n</body></html>\n");
        return sb.toString();
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static List<String> nonAsciiWords(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.split("[\\s\\p{Punct}]+")) {
            if (w.length() >= 2 && hasNonAscii(w)) {
                out.add(w);
                if (out.size() >= 200) {
                    break;
                }
            }
        }
        return out;
    }

    /**
     * True when the bytes carry no charset signal: all below 0x80 and decoding as US-ASCII gives the same
     * text. Keeps 7-bit charsets (ISO-2022-*, UTF-16 of English) that a high-byte test would drop.
     */
    public static boolean asciiEquivalent(byte[] b, Charset cs) {
        for (byte x : b) {
            if (x < 0) {
                return false;
            }
        }
        return new String(b, cs).equals(new String(b, StandardCharsets.US_ASCII));
    }

    static boolean hasNonAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) {
                return true;
            }
        }
        return false;
    }

    static String substitute(String text, Map<Integer, Integer> map) {
        return CharsetTextEncoding.substitute(text, map);
    }

    /**
     * Encodes, dropping unencodable characters (counted), and trims to whole characters within
     * {@code maxBytes}. Returns null on an encoder failure.
     */
    static byte[] encode(String text, Charset cs, int maxBytes, int[] dropped) {
        CharsetEncoder enc = cs.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String kept = CharsetTextEncoding.encodable(text, enc, dropped);
        try {
            ByteBuffer bb = cs.newEncoder().encode(CharBuffer.wrap(kept));
            byte[] all = new byte[bb.remaining()];
            bb.get(all);
            if (all.length <= maxBytes) {
                return all;
            }
            // re-encode a shorter prefix so the chunk ends on a character boundary
            int chars = kept.length();
            while (chars > 0) {
                chars = Math.min(chars - 1, (int) ((long) chars * maxBytes / all.length));
                if (chars > 0 && Character.isLowSurrogate(kept.charAt(chars))) {
                    chars--;
                }
                ByteBuffer b2 = cs.newEncoder().encode(CharBuffer.wrap(kept, 0, chars));
                if (b2.remaining() <= maxBytes) {
                    byte[] out = new byte[b2.remaining()];
                    b2.get(out);
                    return out;
                }
            }
            return null;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    static Map<String, List<String[]>> readTable(Path p) throws IOException {
        Map<String, List<String[]>> t = new LinkedHashMap<>();
        for (String line : Files.readAllLines(p)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("lang\t")) {
                continue;
            }
            String[] f = line.split("\t");
            t.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new String[] {f[0], f[1], f.length > 2 ? f[2] : "native"});
        }
        return t;
    }

    static Map<String, List<Article>> loadArticles(Path f) throws IOException {
        Map<String, List<Article>> bySplit = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(f)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                JsonNode n = JSON.readTree(line);
                String split = WikiSplits.split(f.getParent().getFileName().toString(),
                        n.get("id").asText(), n.get("split").asText());
                bySplit.computeIfAbsent(split, k -> new ArrayList<>())
                        .add(new Article(n.get("id").asText(), n.get("title").asText(), n.get("text").asText()));
            }
        }
        return bySplit;
    }

    record Article(String id, String title, String text) {
    }

    static final class Writers {
        final DataOutputStream bin;
        final Writer meta;

        private Writers(DataOutputStream bin, Writer meta) {
            this.bin = bin;
            this.meta = meta;
        }

        static Writers open(Path outDir, String split, String charset) {
            try {
                Path d = outDir.resolve(split);
                Files.createDirectories(d);
                OutputStream b = new GZIPOutputStream(Files.newOutputStream(d.resolve(charset + ".bin.gz")));
                OutputStream m = new GZIPOutputStream(Files.newOutputStream(d.resolve(charset + ".meta.jsonl.gz")));
                return new Writers(new DataOutputStream(b), new java.io.OutputStreamWriter(m, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        void write(byte[] bytes, String lang, String articleId, String variant, boolean subst, int dropped,
                   int insertedWords) throws IOException {
            bin.writeShort(bytes.length);
            bin.write(bytes);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lang", lang);
            m.put("article", articleId);
            m.put("variant", variant);
            m.put("subst", subst);
            m.put("dropped", dropped);
            if (insertedWords > 0) {
                m.put("k", insertedWords);
            }
            m.put("bytes", bytes.length);
            int high = 0;
            for (byte x : bytes) {
                if (x < 0) {
                    high++;
                }
            }
            m.put("high", high);
            meta.write(JSON.writeValueAsString(m));
            meta.write('\n');
        }

        void close() throws IOException {
            bin.close();
            meta.close();
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
