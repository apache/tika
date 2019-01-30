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
package org.apache.tika.eval.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.PriorityQueue;
import org.apache.tika.eval.tokens.AnalyzerManager;

/**
 * Utility class that reads in a UTF-8 input file with one document per row
 * and outputs the 20000 tokens with the highest document frequencies.
 *
 * The CommmonTokensAnalyzer intentionally drops tokens shorter than 4 characters,
 * but includes bigrams for cjk.
 *
 * It also has a white list for __email__ and __url__ and a black list
 * for common html markup terms.
 */
public class TopCommonTokenCounter {
    private static final String FIELD = "f";
    private static int TOP_N = 20000;
    private static int MIN_DOC_FREQ = 10;
    //these should exist in every list
    static Set<String> WHITE_LIST = new HashSet<>(Arrays.asList(
            new String[] {
                    "___email___",
                    "___url___"
            }
    ));

    //words to ignore
    //these are common 4 letter html markup words that we do
    //not want to count in case of failed markup processing.
    //see: https://issues.apache.org/jira/browse/TIKA-2267?focusedCommentId=15872055&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-15872055
    static Set<String> BLACK_LIST = new HashSet<>(Arrays.asList(
            "span",
            "table",
            "href",
            "head",
            "title",
            "body",
            "html",
            "tagname",
            "lang",
            "style",
            "script",
            "strong",
            "blockquote",
            "form",
            "iframe",
            "section",
            "colspan",
            "rowspan"
    ));

    public static void main(String[] args) throws Exception {
        Path inputFile = Paths.get(args[0]);
        Path commonTokensFile = Paths.get(args[1]);
        TopCommonTokenCounter counter = new TopCommonTokenCounter();
        counter.execute(inputFile, commonTokensFile);
    }

    private void execute(Path inputFile, Path commonTokensFile) throws Exception {
        Path luceneDir = Files.createTempDirectory("tika-eval-lucene-");
        AbstractTokenTFDFPriorityQueue queue = new TokenDFPriorityQueue(TOP_N);
        try {
            Directory directory = FSDirectory.open(luceneDir);
            AnalyzerManager analyzerManager = AnalyzerManager.newInstance(-1);

            Analyzer analyzer = analyzerManager.getCommonTokensAnalyzer();
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
            int maxLen = 1000000;
            int len = 0;
            try (IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
                List<Document> docs = new ArrayList<>();
                try (BufferedReader reader = getReader(inputFile)) {
                    String line = reader.readLine();
                    while (line != null) {
                        len += line.length();
                        Document document = new Document();
                        document.add(new TextField(FIELD, line, Field.Store.NO));
                        docs.add(document);
                        if (len > maxLen) {
                            writer.addDocuments(docs);
                            docs.clear();
                            len = 0;
                        }
                        line = reader.readLine();
                    }
                }
                if (docs.size() > 0) {
                    writer.addDocuments(docs);
                }
                writer.commit();
                writer.flush();
            }
            try (IndexReader reader = DirectoryReader.open(directory)) {
                LeafReader wrappedReader = SlowCompositeReaderWrapper.wrap(reader);
                Terms terms = wrappedReader.terms(FIELD);
                TermsEnum termsEnum = terms.iterator();
                BytesRef bytesRef = termsEnum.next();
                int docsWThisField = wrappedReader.getDocCount(FIELD);
                while (bytesRef != null) {
                    int df = termsEnum.docFreq();
                    long tf = termsEnum.totalTermFreq();
                    if (MIN_DOC_FREQ > -1 && df < MIN_DOC_FREQ) {
                        bytesRef = termsEnum.next();
                        continue;
                    }

                    if (queue.top() == null || queue.size() < TOP_N ||
                            df >= queue.top().df) {
                        String t = bytesRef.utf8ToString();
                        if (! WHITE_LIST.contains(t) && ! BLACK_LIST.contains(t)) {
                            queue.insertWithOverflow(new TokenDFTF(t, df, tf));
                        }

                    }
                    bytesRef = termsEnum.next();
                }
            }
        } finally {
            FileUtils.deleteDirectory(luceneDir.toFile());
        }

        writeTopN(commonTokensFile, queue);


    }

    private BufferedReader getReader(Path inputFile) throws IOException {
        InputStream is = Files.newInputStream(inputFile);
        if (inputFile.toString().endsWith(".gz")) {
            is = new GzipCompressorInputStream(is);
        }
        return new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)
        );
    }

    private static void writeTopN(Path path, AbstractTokenTFDFPriorityQueue queue) throws IOException {
        if (Files.isRegularFile(path)) {
            System.err.println("File "+path.getFileName() + " already exists. Skipping.");
            return;
        }
        Files.createDirectories(path.getParent());
        BufferedWriter writer =
                Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        //add these tokens no matter what
        for (String t : WHITE_LIST) {
            writer.write(t);
            writer.newLine();
        }
        for (TokenDFTF tp : queue.getArray()) {
            writer.write(getRow(sb, tp)+"\n");

        }
        writer.flush();
        writer.close();
    }

    private static String getRow(StringBuilder sb, TokenDFTF tp) {
        sb.setLength(0);
        sb.append(clean(tp.token));
        //sb.append("\t").append(tp.df);
        //sb.append("\t").append(tp.tf);
        return sb.toString();
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private abstract class AbstractTokenTFDFPriorityQueue extends PriorityQueue<TokenDFTF> {

        AbstractTokenTFDFPriorityQueue(int maxSize) {
            super(maxSize);
        }

        public TokenDFTF[] getArray() {
            TokenDFTF[] topN = new TokenDFTF[size()];
            //now we reverse the queue
            TokenDFTF term = pop();
            int i = topN.length-1;
            while (term != null && i > -1) {
                topN[i--] = term;
                term = pop();
            }
            return topN;
        }
    }

    private class TokenDFTF {

        final String token;
        final int df;
        final long tf;

        public TokenDFTF(String token, int df, long tf) {
            this.token = token;
            this.df = df;
            this.tf = tf;
        }


        public long getTF() {
            return tf;
        }

        public int getDF() {
            return df;
        }

        public String getToken() {
            return token;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TokenDFTF tokenDFTF = (TokenDFTF) o;

            if (df != tokenDFTF.df) return false;
            if (tf != tokenDFTF.tf) return false;
            return token != null ? token.equals(tokenDFTF.token) : tokenDFTF.token == null;
        }

        @Override
        public int hashCode() {
            int result = token != null ? token.hashCode() : 0;
            result = 31 * result + df;
            result = 31 * result + (int) (tf ^ (tf >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "TokenDFTF{" +
                    "token='" + token + '\'' +
                    ", df=" + df +
                    ", tf=" + tf +
                    '}';
        }
    }

    private class TokenDFPriorityQueue extends AbstractTokenTFDFPriorityQueue {

        TokenDFPriorityQueue(int maxSize) {
            super(maxSize);
        }

        @Override
        protected boolean lessThan(TokenDFTF arg0, TokenDFTF arg1) {
            if (arg0.df < arg1.df) {
                return true;
            } else if (arg0.df > arg1.df) {
                return false;
            }
            return arg1.token.compareTo(arg0.token) < 0;
        }

        public TokenDFTF[] getArray() {
            TokenDFTF[] topN = new TokenDFTF[size()];
            //now we reverse the queue
            TokenDFTF term = pop();
            int i = topN.length-1;
            while (term != null && i > -1) {
                topN[i--] = term;
                term = pop();
            }
            return topN;
        }
    }
}
