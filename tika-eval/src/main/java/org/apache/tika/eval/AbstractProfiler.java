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

package org.apache.tika.eval;


import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.optimaize.langdetect.DetectedLanguage;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.batch.fs.FSProperties;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.db.ColInfo;
import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.ExtractReaderException;
import org.apache.tika.eval.io.IDBWriter;
import org.apache.tika.eval.tokens.AnalyzerManager;
import org.apache.tika.eval.tokens.CommonTokenCountManager;
import org.apache.tika.eval.tokens.CommonTokenResult;
import org.apache.tika.eval.tokens.TokenCounter;
import org.apache.tika.eval.tokens.TokenIntPair;
import org.apache.tika.eval.tokens.TokenStatistics;
import org.apache.tika.eval.util.LanguageIDWrapper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractProfiler extends FileResourceConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProfiler.class);


    private static final String[] EXTRACT_EXTENSIONS = {
            ".json",
            ".txt",
            ""
    };

    private static final String[] COMPRESSION_EXTENSIONS = {
            "",
            ".bz2",
            ".gzip",
            ".zip",
    };
    static final long NON_EXISTENT_FILE_LENGTH = -1l;

    public static TableInfo REF_EXTRACT_EXCEPTION_TYPES = new TableInfo("ref_extract_exception_types",
            new ColInfo(Cols.EXTRACT_EXCEPTION_ID, Types.INTEGER),
            new ColInfo(Cols.EXTRACT_EXCEPTION_DESCRIPTION, Types.VARCHAR, 128)
    );


    public static TableInfo REF_PARSE_ERROR_TYPES = new TableInfo("ref_parse_error_types",
            new ColInfo(Cols.PARSE_ERROR_ID, Types.INTEGER),
            new ColInfo(Cols.PARSE_ERROR_DESCRIPTION, Types.VARCHAR, 128)
    );

    public static TableInfo REF_PARSE_EXCEPTION_TYPES = new TableInfo("ref_parse_exception_types",
            new ColInfo(Cols.PARSE_EXCEPTION_ID, Types.INTEGER),
            new ColInfo(Cols.PARSE_EXCEPTION_DESCRIPTION, Types.VARCHAR, 128)
    );

    public static final String TRUE = Boolean.toString(true);
    public static final String FALSE = Boolean.toString(false);


    protected static final AtomicInteger ID = new AtomicInteger();

    private final static String UNKNOWN_EXTENSION = "unk";
    //make this configurable
    private final static String DIGEST_KEY = "X-TIKA:digest:MD5";

    private static CommonTokenCountManager commonTokenCountManager;
    private String lastExtractExtension = null;

    AnalyzerManager analyzerManager;
    TokenCounter tokenCounter;


    public enum EXCEPTION_TYPE {
        RUNTIME,
        ENCRYPTION,
        ACCESS_PERMISSION,
        UNSUPPORTED_VERSION,
    }

    /**
     * If information was gathered from the log file about
     * a parse error
     */
    public enum PARSE_ERROR_TYPE {
        OOM,
        TIMEOUT
    }

    public static TableInfo MIME_TABLE = new TableInfo("mimes",
            new ColInfo(Cols.MIME_ID, Types.INTEGER, "PRIMARY KEY"),
            new ColInfo(Cols.MIME_STRING, Types.VARCHAR, 256),
            new ColInfo(Cols.FILE_EXTENSION, Types.VARCHAR, 12)
    );

    private static Pattern FILE_NAME_CLEANER = Pattern.compile("\\.(json|txt)(\\.(bz2|gz|zip))?$");


    final static int FILE_PATH_MAX_LEN = 1024;//max len for varchar for file_path
    int maxContentLength = 10000000;
    int maxContentLengthForLangId = 50000;
    int maxTokens = 200000;


    //these remove runtime info from the stacktraces so
    //that actual causes can be counted.
    private final static Pattern CAUSED_BY_SNIPPER =
            Pattern.compile("(Caused by: [^:]+):[^\\r\\n]+");

    private final static Pattern ACCESS_PERMISSION_EXCEPTION =
            Pattern.compile("org\\.apache\\.tika\\.exception\\.AccessPermissionException");
    private final static Pattern ENCRYPTION_EXCEPTION =
            Pattern.compile("org\\.apache\\.tika.exception\\.EncryptedDocumentException");

    private TikaConfig config = TikaConfig.getDefaultConfig();//TODO: allow configuration
    final LanguageIDWrapper langIder;
    protected IDBWriter writer;

    /**
     *
     * @param p path to the common_tokens directory.  If this is null, try to load from classPath
     * @throws IOException
     */
    public static void loadCommonTokens(Path p, String defaultLangCode) throws IOException {
        commonTokenCountManager = new CommonTokenCountManager(p, defaultLangCode);
    }

    public AbstractProfiler(ArrayBlockingQueue<FileResource> fileQueue,
                            IDBWriter writer) {
        super(fileQueue);
        this.writer = writer;
        langIder = new LanguageIDWrapper();
        initAnalyzersAndTokenCounter(maxTokens);
    }

    private void initAnalyzersAndTokenCounter(int maxTokens) {
        try {
            analyzerManager = AnalyzerManager.newInstance(maxTokens);
            tokenCounter = new TokenCounter(analyzerManager.getGeneralAnalyzer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Truncate the content string if greater than this length to this length
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
    }

    /**
     * Add a LimitTokenCountFilterFactory if > -1
     *
     * @param maxTokens
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        initAnalyzersAndTokenCounter(maxTokens);
    }


    protected void writeExtractException(TableInfo extractExceptionTable, String containerId,
                                         String filePath, ExtractReaderException.TYPE type) throws IOException {
        Map<Cols, String> data = new HashMap<>();
        data.put(Cols.CONTAINER_ID, containerId);
        data.put(Cols.FILE_PATH, filePath);
        data.put(Cols.EXTRACT_EXCEPTION_ID, Integer.toString(type.ordinal()));
        writer.writeRow(extractExceptionTable, data);

    }

    protected void writeProfileData(EvalFilePaths fps, int i, Metadata m,
                                    String fileId, String containerId,
                                    List<Integer> numAttachments, TableInfo profileTable) {

        Map<Cols, String> data = new HashMap<>();
        data.put(Cols.ID, fileId);
        data.put(Cols.CONTAINER_ID, containerId);
        data.put(Cols.MD5, m.get(DIGEST_KEY));

        if ( i < numAttachments.size()) {
            data.put(Cols.NUM_ATTACHMENTS, Integer.toString(numAttachments.get(i)));
        }
        data.put(Cols.ELAPSED_TIME_MILLIS, getTime(m));
        data.put(Cols.NUM_METADATA_VALUES,
                Integer.toString(countMetadataValues(m)));

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
            data.put(Cols.FILE_NAME, getFileName(m.get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH)));
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
        data.put(Cols.NUM_METADATA_VALUES,
                Integer.toString(numMetadataValues));

        data.put(Cols.ELAPSED_TIME_MILLIS,
                getTime(m));

        String content = getContent(m);
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

    /**
     * Checks to see if metadata is null or content is empty (null or only whitespace).
     * If any of these, then this does no processing, and the fileId is not
     * entered into the content table.
     *
     * @param fileId
     * @param m
     * @param fieldName
     * @param contentsTable
     */
    protected void writeContentData(String fileId, Metadata m,
                                    String fieldName, TableInfo contentsTable) throws IOException {
        if (m == null) {
            return;
        }
        Map<Cols, String> data = new HashMap<>();
        String content = getContent(m, maxContentLength, data);
        if (content == null || content.trim().length() == 0) {
            return;
        }
        tokenCounter.clear(fieldName);
        tokenCounter.add(fieldName, content);

        data.put(Cols.ID, fileId);
        data.put(Cols.CONTENT_LENGTH, Integer.toString(content.length()));
        langid(m, data);
        String langid = data.get(Cols.LANG_ID_1);
        langid = (langid == null) ? "" : langid;

        writeTokenCounts(data, fieldName, tokenCounter);
        CommonTokenResult commonTokenResult = null;
        try {
            commonTokenResult = commonTokenCountManager.countTokenOverlaps(langid,
                    tokenCounter.getTokens(fieldName));
        } catch (IOException e) {
            LOG.error("{}", e.getMessage(), e);
        }
        data.put(Cols.COMMON_TOKENS_LANG, commonTokenResult.getLangCode());
        data.put(Cols.NUM_COMMON_TOKENS, Integer.toString(commonTokenResult.getCommonTokens()));
        TokenStatistics tokenStatistics = tokenCounter.getTokenStatistics(fieldName);
        data.put(Cols.NUM_UNIQUE_TOKENS,
                Integer.toString(tokenStatistics.getTotalUniqueTokens()));
        data.put(Cols.NUM_TOKENS,
                Integer.toString(tokenStatistics.getTotalTokens()));
        data.put(Cols.NUM_ALPHABETIC_TOKENS,
                Integer.toString(commonTokenResult.getAlphabeticTokens()));

        data.put(Cols.TOKEN_ENTROPY_RATE,
                Double.toString(tokenStatistics.getEntropy()));
        SummaryStatistics summStats = tokenStatistics.getSummaryStatistics();
        data.put(Cols.TOKEN_LENGTH_SUM,
                Integer.toString((int) summStats.getSum()));

        data.put(Cols.TOKEN_LENGTH_MEAN,
                Double.toString(summStats.getMean()));

        data.put(Cols.TOKEN_LENGTH_STD_DEV,
                Double.toString(summStats.getStandardDeviation()));
        unicodeBlocks(m, data);
        try {
            writer.writeRow(contentsTable, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String getTime(Metadata m) {
        String elapsed = "-1";

        String v = m.get(RecursiveParserWrapper.PARSE_TIME_MILLIS);
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

        String fullTrace = metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX + "runtime");

        if (fullTrace == null) {
            fullTrace = metadata.get(RecursiveParserWrapper.EMBEDDED_EXCEPTION);
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

            data.put(Cols.PARSE_EXCEPTION_ID,
                    Integer.toString(EXCEPTION_TYPE.RUNTIME.ordinal()));

            data.put(Cols.ORIG_STACK_TRACE, fullTrace);
            //TikaExceptions can have object ids, as in the "@2b1ea6ee" in:
            //org.apache.tika.exception.TikaException: TIKA-198: Illegal
            //IOException from org.apache.tika.parser.microsoft.OfficeParser@2b1ea6ee
            //For reporting purposes, let's snip off the object id so that we can more
            //easily count exceptions.
            String sortTrace = ExceptionUtils.trimMessage(fullTrace);

            matcher = CAUSED_BY_SNIPPER.matcher(sortTrace);
            sortTrace = matcher.replaceAll("$1");
            sortTrace = sortTrace.replaceAll("org.apache.tika.", "o.a.t.");
            data.put(Cols.SORT_STACK_TRACE, sortTrace);
        }
    }

    /**
     * Get the content and record in the data {@link Cols#CONTENT_TRUNCATED_AT_MAX_LEN} whether the string was truncated
     *
     * @param metadata
     * @param maxLength
     * @param data
     * @return
     */
    protected static String getContent(Metadata metadata, int maxLength, Map<Cols, String> data) {
        data.put(Cols.CONTENT_TRUNCATED_AT_MAX_LEN, "FALSE");
        String c = getContent(metadata);
        if (maxLength > -1 && c.length() > maxLength) {
            c = c.substring(0, maxLength);
            data.put(Cols.CONTENT_TRUNCATED_AT_MAX_LEN, "TRUE");
        }
        return c;

    }
    protected static String getContent(Metadata metadata) {
        if (metadata == null) {
            return "";
        }
        String c = metadata.get(RecursiveParserWrapper.TIKA_CONTENT);
        if (c == null) {
            return "";
        }
        return c;
    }

    void unicodeBlocks(Metadata metadata, Map<Cols, String> data) {
        String content = getContent(metadata);
        if (content.length() < 200) {
            return;
        }
        String s = content;
        if (content.length() > maxContentLengthForLangId) {
            s = content.substring(0, maxContentLengthForLangId);
        }
        Map<String, Integer> m = new HashMap<>();
        Reader r = new StringReader(s);
        try {
            int c = r.read();
            while (c != -1) {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                String blockString = (block == null) ? "NULL" : block.toString();
                Integer i = m.get(blockString);
                if (i == null) {
                    i = 0;
                }
                i++;
                if (block == null) {
                    blockString = "NULL";
                }
                m.put(blockString, i);
                c = r.read();
            }
        } catch (IOException e) {
            LOG.warn("IOException", e);
        }

        List<Pair<String, Integer>> pairs = new ArrayList<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            pairs.add(Pair.of(e.getKey(), e.getValue()));
        }
        Collections.sort(pairs, new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 20 && i < pairs.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(pairs.get(i).getKey()+": "+pairs.get(i).getValue());
        }
        data.put(Cols.UNICODE_CHAR_BLOCKS, sb.toString());
    }

    void langid(Metadata metadata, Map<Cols, String> data) {
        String content = getContent(metadata);
        if (content.length() < 50) {
            return;
        }
        String s = content;
        if (content.length() > maxContentLengthForLangId) {
            s = content.substring(0, maxContentLengthForLangId);
        }
        List<DetectedLanguage> probabilities = langIder.getProbabilities(s);
        if (probabilities.size() > 0) {
            data.put(Cols.LANG_ID_1, getLangString(probabilities.get(0)));
            data.put(Cols.LANG_ID_PROB_1,
            Double.toString(probabilities.get(0).getProbability()));
        }
        if (probabilities.size() > 1) {
            data.put(Cols.LANG_ID_2, getLangString(probabilities.get(1)));
            data.put(Cols.LANG_ID_PROB_2,
            Double.toString(probabilities.get(1).getProbability()));
        }
    }

    private String getLangString(DetectedLanguage detectedLanguage) {
        //So that we have mapping between lang id and common-tokens file names
        String lang = detectedLanguage.getLocale().getLanguage();
        if ("zh".equals(lang)) {
            if (detectedLanguage.getLocale().getRegion().isPresent()) {
                lang += "-" + detectedLanguage.getLocale().getRegion().get().toLowerCase(Locale.US);
            } else {
                //hope for the best
                lang += "-cn";
            }
        }
        return lang;
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

    void writeTokenCounts(Map<Cols, String> data, String field,
                          TokenCounter tokenCounter) {


        int stops = 0;
        int i = 0;
        StringBuilder sb = new StringBuilder();
        TokenStatistics tokenStatistics = tokenCounter.getTokenStatistics(field);
        for (TokenIntPair t : tokenStatistics.getTopN()) {
            if (i++ > 0) {
                sb.append(" | ");
            }
            sb.append(t.getToken() + ": " + t.getValue());
        }

        data.put(Cols.TOP_N_TOKENS, sb.toString());
    }


    public void closeWriter() throws IOException {
        writer.close();
    }


    /**
     *
     * @param metadata
     * @param extracts
     * @return evalfilepaths for files if crawling an extract directory
     */
    protected EvalFilePaths getPathsFromExtractCrawl(Metadata metadata,
                                                     Path extracts) {
        String relExtractFilePath = metadata.get(FSProperties.FS_REL_PATH);
        Matcher m = FILE_NAME_CLEANER.matcher(relExtractFilePath);
        Path relativeSourceFilePath = Paths.get(m.replaceAll(""));
        //just try slapping the relextractfilepath on the extractdir
        Path extractFile = extracts.resolve(relExtractFilePath);
        if (! Files.isRegularFile(extractFile)) {
            //if that doesn't work, try to find the right extract file.
            //This is necessary if crawling extractsA and trying to find a file in
            //extractsB that is not in the same format: json vs txt or compressed
            extractFile = findFile(extracts, relativeSourceFilePath);
        }
        return new EvalFilePaths(relativeSourceFilePath, extractFile);
    }
    //call this if the crawler is crawling through the src directory
    protected EvalFilePaths getPathsFromSrcCrawl(Metadata metadata, Path srcDir,
                                                 Path extracts) {
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
     *
     * @param extractRootDir
     * @param relativeSourceFilePath
     * @return extractFile or null if couldn't find one.
     */
    private Path findFile(Path extractRootDir, Path relativeSourceFilePath) {
        String relSrcFilePathString = relativeSourceFilePath.toString();
        if (lastExtractExtension != null) {
            Path candidate = extractRootDir.resolve(relSrcFilePathString+lastExtractExtension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        for (String ext : EXTRACT_EXTENSIONS) {
            for (String compress : COMPRESSION_EXTENSIONS) {
                Path candidate = extractRootDir.resolve(relSrcFilePathString+ext+compress);
                if (Files.isRegularFile(candidate)) {
                    lastExtractExtension = ext+compress;
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

    /**
     *
     * @param list
     * @return empty list if input list is empty or null
     */
    static List<Integer> countAttachments(List<Metadata> list) {
        List<Integer> ret = new ArrayList<>();
        if (list == null || list.size() == 0) {
            return ret;
        }
        //container document attachment count = list.size()-1
        ret.add(list.size()-1);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 1; i < list.size(); i++) {
            String path = list.get(i).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH);
            if (path == null) {
                //shouldn't ever happen
                continue;
            }
            String[] parts = path.split("/");
            StringBuilder parent = new StringBuilder();
            for (int end = 1; end < parts.length-1; end++) {
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
            Integer count = counts.get(list.get(i).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
            if (count == null) {
                count = 0;
            }
            ret.add(i, count);
        }
        return ret;


    }

    private static void join(String delimiter, StringBuilder sb, String[] parts, int start, int end) {
        for (int i = start; i <= end; i++) {
            sb.append(delimiter);
            sb.append(parts[i]);
        }
    }
}

