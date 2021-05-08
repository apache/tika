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
package org.apache.tika.eval.app.tools;

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
import java.util.Objects;
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

import org.apache.tika.eval.core.tokens.AnalyzerManager;
import org.apache.tika.eval.core.tokens.URLEmailNormalizingFilterFactory;
import org.apache.tika.utils.ProcessUtils;

/**
 * Utility class that reads in a UTF-8 input file with one document per row
 * and outputs the 20000 tokens with the highest document frequencies.
 * <p>
 * The CommmonTokensAnalyzer intentionally drops tokens shorter than 4 characters,
 * but includes bigrams for cjk.
 * <p>
 * It also has a include list for __email__ and __url__ and a skip list
 * for common html markup terms.
 */
public class TopCommonTokenCounter {

    private static final String FIELD = "f";
    //these should exist in every list
    static Set<String> INCLUDE_LIST = new HashSet<>(Arrays.asList(
            new String[]{URLEmailNormalizingFilterFactory.URL,
                    URLEmailNormalizingFilterFactory.EMAIL}));
    //words to ignore
    //these are common 4 letter html markup words that we do
    //not want to count in case of failed markup processing.
    //see: https://issues.apache.org/jira/browse/TIKA-2267?focusedCommentId=15872055&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-15872055
    static Set<String> SKIP_LIST = new HashSet<>(
            Arrays.asList("span", "table", "href", "head", "title", "body", "html", "tagname",
                    "lang", "style", "script", "strong", "blockquote", "form", "iframe", "section",
                    "colspan", "rowspan"));
    private static String LICENSE =
            "# Licensed to the Apache Software Foundation (ASF) under one or more\n" +
                    "# contributor license agreements.  See the NOTICE file distributed with\n" +
                    "# this work for additional information regarding copyright ownership.\n" +
                    "# The ASF licenses this file to You under the Apache License, Version 2.0\n" +
                    "# (the \"License\"); you may not use this file except in compliance with\n" +
                    "# the License.  You may obtain a copy of the License at\n" + "#\n" +
                    "#     http://www.apache.org/licenses/LICENSE-2.0\n" + "#\n" +
                    "# Unless required by applicable law or agreed to in writing, software\n" +
                    "# distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "# See the License for the specific language governing permissions and\n" +
                    "# limitations under the License.\n" + "#\n";
    private static int TOP_N = 30000;
    private static int MIN_DOC_FREQ = 10;

    public static void main(String[] args) throws Exception {
        Path commonTokensFile = Paths.get(args[0]);
        List<Path> inputFiles = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            inputFiles.add(Paths.get(ProcessUtils.unescapeCommandLine(args[i])));
        }
        TopCommonTokenCounter counter = new TopCommonTokenCounter();
        if (Files.exists(commonTokensFile)) {
            System.err.println(
                    commonTokensFile.getFileName().toString() + " exists. I'm skipping this.");
            return;
        }
        counter.execute(commonTokensFile, inputFiles);
    }

    private static void writeTopN(Path path, long totalDocs, long sumDocFreqs,
                                  long sumTotalTermFreqs, long uniqueTerms,
                                  AbstractTokenTFDFPriorityQueue queue) throws IOException {
        if (Files.isRegularFile(path)) {
            System.err.println("File " + path.getFileName() + " already exists. Skipping.");
            return;
        }
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            writer.write(LICENSE);
            writer.write("#DOC_COUNT\t" + totalDocs + "\n");
            writer.write("#SUM_DOC_FREQS\t" + sumDocFreqs + "\n");
            writer.write("#SUM_TERM_FREQS\t" + sumTotalTermFreqs + "\n");
            writer.write("#UNIQUE_TERMS\t" + uniqueTerms + "\n");
            writer.write("#TOKEN\tDOCFREQ\tTERMFREQ\n");
            //add these tokens no matter what
            for (String t : INCLUDE_LIST) {
                writer.write(t);
                writer.newLine();
            }
            for (TokenDFTF tp : queue.getArray()) {
                writer.write(getRow(sb, tp) + "\n");

            }
            writer.flush();
        }
    }

    private static String getRow(StringBuilder sb, TokenDFTF tp) {
        sb.setLength(0);
        sb.append(clean(tp.token));
        sb.append("\t").append(tp.df);
        sb.append("\t").append(tp.tf);
        return sb.toString();
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private void execute(Path commonTokensFile, List<Path> inputFiles) throws Exception {
        Path luceneDir = Files.createTempDirectory("tika-eval-lucene-");
        AbstractTokenTFDFPriorityQueue queue = new TokenDFPriorityQueue(TOP_N);
        long totalDocs = -1;
        long sumDocFreqs = -1;
        long sumTotalTermFreqs = -1;
        long uniqueTerms = -1;
        try (Directory directory = FSDirectory.open(luceneDir)) {

            AnalyzerManager analyzerManager = AnalyzerManager.newInstance(-1);

            Analyzer analyzer = analyzerManager.getCommonTokensAnalyzer();
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
            int maxLen = 1000000;
            int len = 0;
            try (IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
                List<Document> docs = new ArrayList<>();
                for (Path inputFile : inputFiles) {
                    //total hack
                    boolean isLeipzig = false;
                    if (inputFile.getFileName().toString().contains("-sentences.txt")) {
                        isLeipzig = true;
                    }
                    int lines = 0;
                    try (BufferedReader reader = getReader(inputFile)) {
                        String line = reader.readLine();
                        while (line != null) {
                            if (isLeipzig) {
                                int tab = line.indexOf("\t");
                                if (tab > -1) {
                                    line = line.substring(tab + 1);
                                }
                            }
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
                            if (++lines % 100000 == 0) {
                                System.out.println(
                                        "processed " + lines + " for " + inputFile.getFileName() +
                                                " :: " + commonTokensFile.toAbsolutePath());
                            }
                        }
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
                totalDocs = wrappedReader.getDocCount(FIELD);
                sumDocFreqs = wrappedReader.getSumDocFreq(FIELD);
                sumTotalTermFreqs = wrappedReader.getSumTotalTermFreq(FIELD);

                Terms terms = wrappedReader.terms(FIELD);
                TermsEnum termsEnum = terms.iterator();
                BytesRef bytesRef = termsEnum.next();
                int docsWThisField = wrappedReader.getDocCount(FIELD);
                while (bytesRef != null) {
                    uniqueTerms++;
                    int df = termsEnum.docFreq();
                    long tf = termsEnum.totalTermFreq();
                    if (MIN_DOC_FREQ > -1 && df < MIN_DOC_FREQ) {
                        bytesRef = termsEnum.next();
                        continue;
                    }

                    if (queue.top() == null || queue.size() < TOP_N || df >= queue.top().df) {
                        String t = bytesRef.utf8ToString();
                        if (!SKIP_LIST.contains(t)) {
                            queue.insertWithOverflow(new TokenDFTF(t, df, tf));
                        }

                    }
                    bytesRef = termsEnum.next();
                }
            }
        } finally {
            FileUtils.deleteDirectory(luceneDir.toFile());
        }

        writeTopN(commonTokensFile, totalDocs, sumDocFreqs, sumTotalTermFreqs, uniqueTerms, queue);


    }

    private BufferedReader getReader(Path inputFile) throws IOException {
        InputStream is = Files.newInputStream(inputFile);
        if (inputFile.toString().endsWith(".gz")) {
            is = new GzipCompressorInputStream(is);
        }
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private abstract class AbstractTokenTFDFPriorityQueue extends PriorityQueue<TokenDFTF> {

        AbstractTokenTFDFPriorityQueue(int maxSize) {
            super(maxSize);
        }

        public TokenDFTF[] getArray() {
            TokenDFTF[] topN = new TokenDFTF[size()];
            //now we reverse the queue
            TokenDFTF term = pop();
            int i = topN.length - 1;
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TokenDFTF tokenDFTF = (TokenDFTF) o;

            if (df != tokenDFTF.df) {
                return false;
            }
            if (tf != tokenDFTF.tf) {
                return false;
            }
            return Objects.equals(token, tokenDFTF.token);
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
            return "TokenDFTF{" + "token='" + token + '\'' + ", df=" + df + ", tf=" + tf + '}';
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
            int i = topN.length - 1;
            while (term != null && i > -1) {
                topN[i--] = term;
                term = pop();
            }
            return topN;
        }
    }
}
