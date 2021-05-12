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

package org.apache.tika.eval.app;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.batch.fs.FSProperties;
import org.apache.tika.eval.app.db.ColInfo;
import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.TableInfo;
import org.apache.tika.eval.app.io.ExtractReaderException;
import org.apache.tika.eval.app.io.IDBWriter;
import org.apache.tika.eval.core.langid.LanguageIDWrapper;
import org.apache.tika.eval.core.textstats.BasicTokenCountStatsCalculator;
import org.apache.tika.eval.core.textstats.CommonTokens;
import org.apache.tika.eval.core.textstats.CompositeTextStatsCalculator;
import org.apache.tika.eval.core.textstats.ContentLengthCalculator;
import org.apache.tika.eval.core.textstats.TextStatsCalculator;
import org.apache.tika.eval.core.textstats.TokenEntropy;
import org.apache.tika.eval.core.textstats.TokenLengths;
import org.apache.tika.eval.core.textstats.TopNTokens;
import org.apache.tika.eval.core.textstats.UnicodeBlockCounter;
import org.apache.tika.eval.core.tokens.AnalyzerManager;
import org.apache.tika.eval.core.tokens.CommonTokenCountManager;
import org.apache.tika.eval.core.tokens.CommonTokenResult;
import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.eval.core.tokens.TokenIntPair;
import org.apache.tika.eval.core.util.ContentTagParser;
import org.apache.tika.eval.core.util.ContentTags;
import org.apache.tika.eval.core.util.EvalExceptionUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.ToXMLContentHandler;

public abstract class AbstractProfiler extends FileResourceConsumer {

    public static final String TRUE = Boolean.toString(true);
    public static final String FALSE = Boolean.toString(false);
    protected static final AtomicInteger ID = new AtomicInteger();
    static final long NON_EXISTENT_FILE_LENGTH = -1l;
    final static int FILE_PATH_MAX_LEN = 1024;//max len for varchar for file_path
    private static final Logger LOG = LoggerFactory.getLogger(AbstractProfiler.class);
    private static final String[] EXTRACT_EXTENSIONS = {".json", ".txt", ""};
    private static final String[] COMPRESSION_EXTENSIONS = {"", ".bz2", ".gzip", ".zip",};
    private static final String ZERO = "0";
    private static final String UNKNOWN_EXTENSION = "unk";
    //make this configurable
    private static final String DIGEST_KEY = "X-TIKA:digest:MD5";
    private static final Map<String, Cols> UC_TAGS_OF_INTEREST = initTags();
    private final static Pattern ACCESS_PERMISSION_EXCEPTION =
            Pattern.compile("org\\.apache\\.tika\\.exception\\.AccessPermissionException");
    private final static Pattern ENCRYPTION_EXCEPTION =
            Pattern.compile("org\\.apache\\.tika.exception\\.EncryptedDocumentException");
    public static TableInfo REF_EXTRACT_EXCEPTION_TYPES =
            new TableInfo("ref_extract_exception_types",
                    new ColInfo(Cols.EXTRACT_EXCEPTION_ID, Types.INTEGER),
                    new ColInfo(Cols.EXTRACT_EXCEPTION_DESCRIPTION, Types.VARCHAR, 128));
    public static TableInfo REF_PARSE_ERROR_TYPES =
            new TableInfo("ref_parse_error_types", new ColInfo(Cols.PARSE_ERROR_ID, Types.INTEGER),
                    new ColInfo(Cols.PARSE_ERROR_DESCRIPTION, Types.VARCHAR, 128));
    public static TableInfo REF_PARSE_EXCEPTION_TYPES = new TableInfo("ref_parse_exception_types",
            new ColInfo(Cols.PARSE_EXCEPTION_ID, Types.INTEGER),
            new ColInfo(Cols.PARSE_EXCEPTION_DESCRIPTION, Types.VARCHAR, 128));
    public static TableInfo MIME_TABLE =
            new TableInfo("mimes", new ColInfo(Cols.MIME_ID, Types.INTEGER, "PRIMARY KEY"),
                    new ColInfo(Cols.MIME_STRING, Types.VARCHAR, 256),
                    new ColInfo(Cols.FILE_EXTENSION, Types.VARCHAR, 12));
    private static CommonTokenCountManager COMMON_TOKEN_COUNT_MANAGER;
    private static Pattern FILE_NAME_CLEANER = Pattern.compile("\\.(json|txt)(\\.(bz2|gz|zip))?$");
    private static LanguageIDWrapper LANG_ID = new LanguageIDWrapper();
    protected IDBWriter writer;
    AnalyzerManager analyzerManager;
    int maxContentLength = 10000000;
    int maxContentLengthForLangId = 50000;
    int maxTokens = 200000;
    //TODO: allow configuration
    //private TikaConfig config = TikaConfig.getDefaultConfig();
    CompositeTextStatsCalculator compositeTextStatsCalculator;
    private String lastExtractExtension = null;

    public AbstractProfiler(ArrayBlockingQueue<FileResource> fileQueue, IDBWriter writer) {
        super(fileQueue);
        this.writer = writer;
        LanguageIDWrapper.setMaxTextLength(maxContentLengthForLangId);
        this.compositeTextStatsCalculator = initAnalyzersAndTokenCounter(maxTokens, LANG_ID);
    }

    private static Map<String, Cols> initTags() {
        //simplify this mess
        Map<String, Cols> tmp = new HashMap<>();
        tmp.put("A", Cols.TAGS_A);
        tmp.put("B", Cols.TAGS_B);
        tmp.put("DIV", Cols.TAGS_DIV);
        tmp.put("I", Cols.TAGS_I);
        tmp.put("IMG", Cols.TAGS_IMG);
        tmp.put("LI", Cols.TAGS_LI);
        tmp.put("OL", Cols.TAGS_OL);
        tmp.put("P", Cols.TAGS_P);
        tmp.put("TABLE", Cols.TAGS_TABLE);
        tmp.put("TD", Cols.TAGS_TD);
        tmp.put("TITLE", Cols.TAGS_TITLE);
        tmp.put("TR", Cols.TAGS_TR);
        tmp.put("U", Cols.TAGS_U);
        tmp.put("UL", Cols.TAGS_UL);
        return Collections.unmodifiableMap(tmp);
    }

    /**
     * @param p               path to the common_tokens directory.  If this is null, try to load from classPath
     * @param defaultLangCode this is the language code to use if a common_words list doesn't exist for the
     *                        detected langauge; can be <code>null</code>
     * @throws IOException
     */
    public static void loadCommonTokens(Path p, String defaultLangCode) throws IOException {
        COMMON_TOKEN_COUNT_MANAGER = new CommonTokenCountManager(p, defaultLangCode);
    }

    private static String getFileName(String path) {
        if (path == null) {
            return "";
        }
        //filenameUtils checks for a null byte in the path.
        //it will throw an IllegalArgumentException if there is a null byte.
        //given that we're recording names and not using them on a file path
        //we should ignore this.
        try {
            return FilenameUtils.getName(path);
        } catch (IllegalArgumentException e) {
            LOG.warn("{} in {}", e.getMessage(), path);
        }
        path = path.replaceAll("\u0000", " ");
        try {
            return FilenameUtils.getName(path);
        } catch (IllegalArgumentException e) {
            LOG.warn("Again: {} in {}", e.getMessage(), path);
        }
        //give up
        return "";
    }

    /**
     * Get the content and record in the data {@link Cols#CONTENT_TRUNCATED_AT_MAX_LEN} whether the string was truncated
     *
     * @param contentTags
     * @param maxLength
     * @param data
     * @return
     */
    protected static String truncateContent(ContentTags contentTags, int maxLength,
                                            Map<Cols, String> data) {
        data.put(Cols.CONTENT_TRUNCATED_AT_MAX_LEN, "FALSE");
        if (contentTags == null) {
            return "";
        }
        String c = contentTags.getContent();
        if (maxLength > -1 && c.length() > maxLength) {
            c = c.substring(0, maxLength);
            data.put(Cols.CONTENT_TRUNCATED_AT_MAX_LEN, "TRUE");
        }
        return c;

    }

    protected static ContentTags getContent(EvalFilePaths evalFilePaths, Metadata metadata) {
        if (metadata == null) {
            return ContentTags.EMPTY_CONTENT_TAGS;
        }
        return parseContentAndTags(evalFilePaths, metadata);
    }

    /**
     * @param list
     * @return empty list if input list is empty or null
     */
    static List<Integer> countAttachments(List<Metadata> list) {
        List<Integer> ret = new ArrayList<>();
        if (list == null || list.size() == 0) {
            return ret;
        }
        //container document attachment count = list.size()-1
        ret.add(list.size() - 1);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 1; i < list.size(); i++) {
            String path = list.get(i).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (path == null) {
                //shouldn't ever happen
                continue;
            }
            String[] parts = path.split("/");
            StringBuilder parent = new StringBuilder();
            for (int end = 1; end < parts.length - 1; end++) {
                parent.setLength(0);
                join("/", parent, parts, 1, end);
                String parentPath = parent.toString();
                Integer count = counts.get(parentPath);
                if (count == null) {
                    count = 1;
                } else {
                    count++;
                }
                counts.put(parentPath, count);
            }
        }

        for (int i = 1; i < list.size(); i++) {
            Integer count = counts.get(list.get(i).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
            if (count == null) {
                count = 0;
            }
            ret.add(i, count);
        }
        return ret;


    }

    private static void join(String delimiter, StringBuilder sb, String[] parts, int start,
                             int end) {
        for (int i = start; i <= end; i++) {
            sb.append(delimiter);
            sb.append(parts[i]);
        }
    }

    private static ContentTags parseContentAndTags(EvalFilePaths evalFilePaths, Metadata metadata) {
        String s = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        if (s == null || s.length() == 0) {
            return ContentTags.EMPTY_CONTENT_TAGS;
        }

        String handlerClass = metadata.get(TikaCoreProperties.TIKA_CONTENT_HANDLER);
        if (evalFilePaths.getExtractFile().getFileName().toString().toLowerCase(Locale.ENGLISH)
                .endsWith(".html")) {
            try {
                return ContentTagParser.parseHTML(s, UC_TAGS_OF_INTEREST.keySet());
            } catch (IOException | SAXException e) {
                LOG.warn("Problem parsing html in {}; backing off to treat string as text",
                        evalFilePaths.getExtractFile().toAbsolutePath().toString(), e);

                return new ContentTags(s, true);
            }
        } else if (
                evalFilePaths.getExtractFile().getFileName().toString().toLowerCase(Locale.ENGLISH)
                        .endsWith(".xhtml") || (handlerClass != null &&
                        handlerClass.equals(ToXMLContentHandler.class.getSimpleName()))) {
            try {
                return ContentTagParser.parseXML(s, UC_TAGS_OF_INTEREST.keySet());
            } catch (TikaException | IOException | SAXException e) {
                LOG.warn("Problem parsing xhtml in {}; backing off to html parser",
                        evalFilePaths.getExtractFile().toAbsolutePath().toString(), e);
                try {
                    ContentTags contentTags =
                            ContentTagParser.parseHTML(s, UC_TAGS_OF_INTEREST.keySet());
                    contentTags.setParseException(true);
                    return contentTags;
                } catch (IOException | SAXException e2) {
                    LOG.warn("Problem parsing html in {}; backing off to treat string as text",
                            evalFilePaths.getExtractFile().toAbsolutePath().toString(), e2);
                }
                return new ContentTags(s, true);
            }
        }
        return new ContentTags(s);
    }

    private CompositeTextStatsCalculator initAnalyzersAndTokenCounter(int maxTokens,
                                                                      LanguageIDWrapper langIder) {
        analyzerManager = AnalyzerManager.newInstance(maxTokens);
        List<TextStatsCalculator> calculators = new ArrayList<>();
        calculators.add(new CommonTokens(COMMON_TOKEN_COUNT_MANAGER));
        calculators.add(new TokenEntropy());
        calculators.add(new TokenLengths());
        calculators.add(new TopNTokens(10));
        calculators.add(new BasicTokenCountStatsCalculator());
        calculators.add(new ContentLengthCalculator());
        calculators.add(new UnicodeBlockCounter(maxContentLengthForLangId));

        return new CompositeTextStatsCalculator(calculators, analyzerManager.getGeneralAnalyzer(),
                langIder);
    }

    /**
     * Truncate the content string if greater than this length to this length
     *
     * @param maxContentLength
     */
    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    /**
     * Truncate content string if greater than this length to this length for lang id
     *
     * @param maxContentLengthForLangId
     */
    public void setMaxContentLengthForLangId(int maxContentLengthForLangId) {
        this.maxContentLengthForLangId = maxContentLengthForLangId;
        LanguageIDWrapper.setMaxTextLength(maxContentLengthForLangId);
    }

    /**
     * Add a LimitTokenCountFilterFactory if &gt; -1
     *
     * @param maxTokens
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        initAnalyzersAndTokenCounter(maxTokens, new LanguageIDWrapper());
    }

    protected void writeExtractException(TableInfo extractExceptionTable, String containerId,
                                         String filePath, ExtractReaderException.TYPE type)
            throws IOException {
        Map<Cols, String> data = new HashMap<>();
        data.put(Cols.CONTAINER_ID, containerId);
        data.put(Cols.FILE_PATH, filePath);
        data.put(Cols.EXTRACT_EXCEPTION_ID, Integer.toString(type.ordinal()));
        writer.writeRow(extractExceptionTable, data);

    }

    protected void writeProfileData(EvalFilePaths fps, int i, ContentTags contentTags, Metadata m,
                                    String fileId, String containerId, List<Integer> numAttachments,
                                    TableInfo profileTable) {

        Map<Cols, String> data = new HashMap<>();
        data.put(Cols.ID, fileId);
        data.put(Cols.CONTAINER_ID, containerId);
        data.put(Cols.MD5, m.get(DIGEST_KEY));

        if (i < numAttachments.size()) {
            data.put(Cols.NUM_ATTACHMENTS, Integer.toString(numAttachments.get(i)));
        }
        data.put(Cols.ELAPSED_TIME_MILLIS, getTime(m));
        data.put(Cols.NUM_METADATA_VALUES, Integer.toString(countMetadataValues(m)));

        Integer nPages = m.getInt(PagedText.N_PAGES);
        if (nPages != null) {
            data.put(Cols.NUM_PAGES, Integer.toString(nPages));
        }

        //if the outer wrapper document
        if (i == 0) {
            data.put(Cols.IS_EMBEDDED, FALSE);
            data.put(Cols.FILE_NAME, fps.getRelativeSourceFilePath().getFileName().toString());
        } else {
            data.put(Cols.IS_EMBEDDED, TRUE);
            data.put(Cols.FILE_NAME, getFileName(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH)));
        }
        String ext = FilenameUtils.getExtension(data.get(Cols.FILE_NAME));
        ext = (ext == null) ? "" : ext.toLowerCase(Locale.US);
        data.put(Cols.FILE_EXTENSION, ext);
        long srcFileLen = getSourceFileLength(m);
        if (srcFileLen > NON_EXISTENT_FILE_LENGTH) {
            data.put(Cols.LENGTH, Long.toString(srcFileLen));
        } else {
            data.put(Cols.LENGTH, "");
        }
        int numMetadataValues = countMetadataValues(m);
        data.put(Cols.NUM_METADATA_VALUES, Integer.toString(numMetadataValues));

        data.put(Cols.ELAPSED_TIME_MILLIS, getTime(m));

        String content = contentTags.getContent();
        if (content == null || content.trim().length() == 0) {
            data.put(Cols.HAS_CONTENT, FALSE);
        } else {
            data.put(Cols.HAS_CONTENT, TRUE);
        }
        getFileTypes(m, data);
        try {
            writer.writeRow(profileTable, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void writeExceptionData(String fileId, Metadata m, TableInfo exceptionTable) {
        Map<Cols, String> data = new HashMap<>();
        getExceptionStrings(m, data);
        if (data.keySet().size() > 0) {
            try {
                data.put(Cols.ID, fileId);
                writer.writeRow(exceptionTable, data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected Map<Class, Object> calcTextStats(ContentTags contentTags) {
/*        if (contentTags == ContentTags.EMPTY_CONTENT_TAGS) {
            return Collections.EMPTY_MAP;
        }*/
        Map<Cols, String> data = new HashMap<>();
        String content = truncateContent(contentTags, maxContentLength, data);
        if (content == null || content.trim().length() == 0) {
            content = "";
        }
        return compositeTextStatsCalculator.calculate(content);
    }

    /**
     * Checks to see if metadata is null or content is empty (null or only whitespace).
     * If any of these, then this does no processing, and the fileId is not
     * entered into the content table.
     *
     * @param fileId
     * @param textStats
     * @param contentsTable
     */
    protected void writeContentData(String fileId, Map<Class, Object> textStats,
                                    TableInfo contentsTable) throws IOException {
        Map<Cols, String> data = new HashMap<>();
        data.put(Cols.ID, fileId);
        if (textStats.containsKey(ContentLengthCalculator.class)) {
            int length = (int) textStats.get(ContentLengthCalculator.class);
            if (length == 0) {
                return;
            }
            data.put(Cols.CONTENT_LENGTH, Integer.toString(length));
        }
        langid(textStats, data);

        writeTokenCounts(textStats, data);
        CommonTokenResult commonTokenResult = (CommonTokenResult) textStats.get(CommonTokens.class);
        if (commonTokenResult != null) {
            data.put(Cols.COMMON_TOKENS_LANG, commonTokenResult.getLangCode());
            data.put(Cols.NUM_UNIQUE_COMMON_TOKENS,
                    Integer.toString(commonTokenResult.getUniqueCommonTokens()));
            data.put(Cols.NUM_COMMON_TOKENS, Integer.toString(commonTokenResult.getCommonTokens()));
            data.put(Cols.NUM_UNIQUE_ALPHABETIC_TOKENS,
                    Integer.toString(commonTokenResult.getUniqueAlphabeticTokens()));
            data.put(Cols.NUM_ALPHABETIC_TOKENS,
                    Integer.toString(commonTokenResult.getAlphabeticTokens()));
        }
        TokenCounts tokenCounts = (TokenCounts) textStats.get(BasicTokenCountStatsCalculator.class);
        if (tokenCounts != null) {

            data.put(Cols.NUM_UNIQUE_TOKENS, Integer.toString(tokenCounts.getTotalUniqueTokens()));
            data.put(Cols.NUM_TOKENS, Integer.toString(tokenCounts.getTotalTokens()));
        }
        if (textStats.get(TokenEntropy.class) != null) {
            data.put(Cols.TOKEN_ENTROPY_RATE,
                    Double.toString((Double) textStats.get(TokenEntropy.class)));
        }

        SummaryStatistics summStats = (SummaryStatistics) textStats.get(TokenLengths.class);
        if (summStats != null) {
            data.put(Cols.TOKEN_LENGTH_SUM, Integer.toString((int) summStats.getSum()));

            data.put(Cols.TOKEN_LENGTH_MEAN, Double.toString(summStats.getMean()));

            data.put(Cols.TOKEN_LENGTH_STD_DEV, Double.toString(summStats.getStandardDeviation()));
        }
        unicodeBlocks(textStats, data);
        try {
            writer.writeRow(contentsTable, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void writeTagData(String fileId, ContentTags contentTags, TableInfo tagsTable) {
        Map<String, Integer> tags = contentTags.getTags();
        if (tags.size() == 0 && contentTags.getParseException() == false) {
            return;
        }
        Map<Cols, String> data = new HashMap<>();
        data.put(Cols.ID, fileId);

        for (Map.Entry<String, Cols> e : UC_TAGS_OF_INTEREST.entrySet()) {
            Integer count = tags.get(e.getKey());
            if (count == null) {
                data.put(e.getValue(), ZERO);
            } else {
                data.put(e.getValue(), Integer.toString(count));
            }
        }

        if (contentTags.getParseException()) {
            data.put(Cols.TAGS_PARSE_EXCEPTION, TRUE);
        } else {
            data.put(Cols.TAGS_PARSE_EXCEPTION, FALSE);
        }
        try {
            writer.writeRow(tagsTable, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String getTime(Metadata m) {
        String elapsed = "-1";

        String v = m.get(TikaCoreProperties.PARSE_TIME_MILLIS);
        if (v != null) {
            return v;
        }
        return elapsed;
    }

    int countMetadataValues(Metadata m) {
        if (m == null) {
            return 0;
        }
        int i = 0;
        for (String n : m.names()) {
            i += m.getValues(n).length;
        }
        return i;
    }

    void getExceptionStrings(Metadata metadata, Map<Cols, String> data) {

        String fullTrace = metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION);

        if (fullTrace == null) {
            fullTrace = metadata.get(TikaCoreProperties.EMBEDDED_EXCEPTION);
        }

        if (fullTrace != null) {
            //check for "expected" exceptions...exceptions
            //that can't be fixed.
            //Do not store trace for "expected" exceptions

            Matcher matcher = ACCESS_PERMISSION_EXCEPTION.matcher(fullTrace);
            if (matcher.find()) {
                data.put(Cols.PARSE_EXCEPTION_ID,
                        Integer.toString(EXCEPTION_TYPE.ACCESS_PERMISSION.ordinal()));
                return;
            }
            matcher = ENCRYPTION_EXCEPTION.matcher(fullTrace);
            if (matcher.find()) {
                data.put(Cols.PARSE_EXCEPTION_ID,
                        Integer.toString(EXCEPTION_TYPE.ENCRYPTION.ordinal()));
                return;
            }

            data.put(Cols.PARSE_EXCEPTION_ID, Integer.toString(EXCEPTION_TYPE.RUNTIME.ordinal()));

            data.put(Cols.ORIG_STACK_TRACE, fullTrace);
            //TikaExceptions can have object ids, as in the "@2b1ea6ee" in:
            //org.apache.tika.exception.TikaException: TIKA-198: Illegal
            //IOException from org.apache.tika.parser.microsoft.OfficeParser@2b1ea6ee
            //For reporting purposes, let's snip off the object id so that we can more
            //easily count exceptions.
            String sortTrace = EvalExceptionUtils.normalize(fullTrace);
            data.put(Cols.SORT_STACK_TRACE, sortTrace);
        }
    }

    void unicodeBlocks(Map<Class, Object> tokenStats, Map<Cols, String> data) {

        Map<String, MutableInt> blocks =
                (Map<String, MutableInt>) tokenStats.get(UnicodeBlockCounter.class);
        List<Pair<String, Integer>> pairs = new ArrayList<>();
        for (Map.Entry<String, MutableInt> e : blocks.entrySet()) {
            pairs.add(Pair.of(e.getKey(), e.getValue().intValue()));
        }
        pairs.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 20 && i < pairs.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(pairs.get(i).getKey()).append(": ").append(pairs.get(i).getValue());
        }
        data.put(Cols.UNICODE_CHAR_BLOCKS, sb.toString());
    }

    void langid(Map<Class, Object> stats, Map<Cols, String> data) {
        List<LanguageResult> probabilities =
                (List<LanguageResult>) stats.get(LanguageIDWrapper.class);

        if (probabilities.size() > 0) {
            data.put(Cols.LANG_ID_1, probabilities.get(0).getLanguage());
            data.put(Cols.LANG_ID_PROB_1, Double.toString(probabilities.get(0).getRawScore()));
        }
        if (probabilities.size() > 1) {
            data.put(Cols.LANG_ID_2, probabilities.get(1).getLanguage());
            data.put(Cols.LANG_ID_PROB_2, Double.toString(probabilities.get(1).getRawScore()));
        }
    }

    void getFileTypes(Metadata metadata, Map<Cols, String> output) {
        if (metadata == null) {
            return;
        }
        String type = metadata.get(Metadata.CONTENT_TYPE);
        if (type == null) {
            return;
        }
        int mimeId = writer.getMimeId(type);
        output.put(Cols.MIME_ID, Integer.toString(mimeId));
    }

    void writeTokenCounts(Map<Class, Object> textStats, Map<Cols, String> data) {
        TokenIntPair[] tokenIntPairs = (TokenIntPair[]) textStats.get(TopNTokens.class);
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (TokenIntPair t : tokenIntPairs) {
            if (i++ > 0) {
                sb.append(" | ");
            }
            sb.append(t.getToken()).append(": ").append(t.getValue());
        }

        data.put(Cols.TOP_N_TOKENS, sb.toString());
    }

    public void closeWriter() throws IOException {
        writer.close();
    }

    /**
     * @param metadata
     * @param extracts
     * @return evalfilepaths for files if crawling an extract directory
     */
    protected EvalFilePaths getPathsFromExtractCrawl(Metadata metadata, Path extracts) {
        String relExtractFilePath = metadata.get(FSProperties.FS_REL_PATH);
        Matcher m = FILE_NAME_CLEANER.matcher(relExtractFilePath);
        Path relativeSourceFilePath = Paths.get(m.replaceAll(""));
        //just try slapping the relextractfilepath on the extractdir
        Path extractFile = extracts.resolve(relExtractFilePath);
        if (!Files.isRegularFile(extractFile)) {
            //if that doesn't work, try to find the right extract file.
            //This is necessary if crawling extractsA and trying to find a file in
            //extractsB that is not in the same format: json vs txt or compressed
            extractFile = findFile(extracts, relativeSourceFilePath);
        }
        return new EvalFilePaths(relativeSourceFilePath, extractFile);
    }

    //call this if the crawler is crawling through the src directory
    protected EvalFilePaths getPathsFromSrcCrawl(Metadata metadata, Path srcDir, Path extracts) {
        Path relativeSourceFilePath = Paths.get(metadata.get(FSProperties.FS_REL_PATH));
        Path extractFile = findFile(extracts, relativeSourceFilePath);
        Path inputFile = srcDir.resolve(relativeSourceFilePath);
        long srcLen = -1l;
        //try to get the length of the source file in case there was an error
        //in both extracts
        try {
            srcLen = Files.size(inputFile);
        } catch (IOException e) {
            LOG.warn("Couldn't get length for: {}", inputFile.toAbsolutePath());
        }
        return new EvalFilePaths(relativeSourceFilePath, extractFile, srcLen);
    }

    /**
     * @param extractRootDir
     * @param relativeSourceFilePath
     * @return extractFile or null if couldn't find one.
     */
    private Path findFile(Path extractRootDir, Path relativeSourceFilePath) {
        String relSrcFilePathString = relativeSourceFilePath.toString();
        if (lastExtractExtension != null) {
            Path candidate = extractRootDir.resolve(relSrcFilePathString + lastExtractExtension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        for (String ext : EXTRACT_EXTENSIONS) {
            for (String compress : COMPRESSION_EXTENSIONS) {
                Path candidate = extractRootDir.resolve(relSrcFilePathString + ext + compress);
                if (Files.isRegularFile(candidate)) {
                    lastExtractExtension = ext + compress;
                    return candidate;
                }
            }
        }
        return null;
    }

    protected long getSourceFileLength(EvalFilePaths fps, List<Metadata> metadataList) {
        if (fps.getSourceFileLength() > NON_EXISTENT_FILE_LENGTH) {
            return fps.getSourceFileLength();
        }
        return getSourceFileLength(metadataList);
    }

    long getSourceFileLength(List<Metadata> metadataList) {
        if (metadataList == null || metadataList.size() < 1) {
            return NON_EXISTENT_FILE_LENGTH;
        }
        return getSourceFileLength(metadataList.get(0));
    }

    long getSourceFileLength(Metadata m) {
        String lenString = m.get(Metadata.CONTENT_LENGTH);
        if (lenString == null) {
            return NON_EXISTENT_FILE_LENGTH;
        }
        try {
            return Long.parseLong(lenString);
        } catch (NumberFormatException e) {
            //swallow
        }
        return NON_EXISTENT_FILE_LENGTH;
    }

    protected long getFileLength(Path p) {
        if (p != null && Files.isRegularFile(p)) {
            try {
                return Files.size(p);
            } catch (IOException e) {
                //swallow
            }
        }
        return NON_EXISTENT_FILE_LENGTH;
    }

    public enum EXCEPTION_TYPE {
        RUNTIME, ENCRYPTION, ACCESS_PERMISSION, UNSUPPORTED_VERSION,
    }

    /**
     * If information was gathered from the log file about
     * a parse error
     */
    public enum PARSE_ERROR_TYPE {
        OOM, TIMEOUT
    }


}

