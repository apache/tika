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

import static org.apache.tika.eval.app.io.ExtractReader.IGNORE_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.TableInfo;
import org.apache.tika.eval.app.io.ExtractReader;
import org.apache.tika.eval.app.io.ExtractReaderException;
import org.apache.tika.eval.core.util.ContentTags;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

//These tests ensure that the comparer is extracting the right information
//into a Map<String,String>.  A full integration test
//should also ensure that the elements are properly being written to the db

public class SimpleComparerTest extends TikaTest {

    private static MockDBWriter WRITER;
    private ExtractComparer comparer = null;

    @BeforeClass
    public static void staticSetUp() throws Exception {
        WRITER = new MockDBWriter();
        AbstractProfiler.loadCommonTokens(
                Paths.get(SimpleComparerTest.class.getResource("/common_tokens").toURI()), "en");
    }

    @Before
    public void setUp() throws Exception {
        WRITER.clear();
        comparer = new ExtractComparer(null, null, Paths.get("extractsA"), Paths.get("extractsB"),
                new ExtractReader(ExtractReader.ALTER_METADATA_LIST.AS_IS, IGNORE_LENGTH,
                        IGNORE_LENGTH), WRITER);
    }

    @Test
    public void testBasic() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file1.pdf.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file1.pdf.json").toPath());

        comparer.compareFiles(fpsA, fpsB);

        List<Map<Cols, String>> tableInfos = WRITER.getTable(ExtractComparer.CONTENT_COMPARISONS);
        Map<Cols, String> row = tableInfos.get(0);
        assertTrue(row.get(Cols.TOP_10_UNIQUE_TOKEN_DIFFS_A)
                .startsWith("1,200: 1 | 120000: 1 | over: 1"));

        tableInfos = WRITER.getTable(ExtractComparer.CONTENTS_TABLE_A);
        row = tableInfos.get(0);
        assertEquals("70", row.get(Cols.CONTENT_LENGTH));
        assertEquals("10", row.get(Cols.NUM_UNIQUE_TOKENS));
        assertEquals("14", row.get(Cols.NUM_TOKENS));
        assertEquals("8", row.get(Cols.NUM_UNIQUE_ALPHABETIC_TOKENS));
        assertEquals("12", row.get(Cols.NUM_ALPHABETIC_TOKENS));
        assertEquals("8", row.get(Cols.NUM_UNIQUE_COMMON_TOKENS));
        assertEquals("12", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("57", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("eng", row.get(Cols.COMMON_TOKENS_LANG));

        tableInfos = WRITER.getTable(ExtractComparer.CONTENTS_TABLE_B);
        row = tableInfos.get(0);
        assertEquals("76", row.get(Cols.CONTENT_LENGTH));
        assertEquals("9", row.get(Cols.NUM_UNIQUE_TOKENS));
        assertEquals("13", row.get(Cols.NUM_TOKENS));
        assertEquals("8", row.get(Cols.NUM_UNIQUE_COMMON_TOKENS));
        assertEquals("10", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("64", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("eng", row.get(Cols.COMMON_TOKENS_LANG));

        tableInfos = WRITER.getTable(ExtractComparer.PROFILES_A);
        row = tableInfos.get(0);
        assertEquals("2", row.get(Cols.NUM_PAGES));

    }

    @Test
    public void testBasicSpanish() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file12_es.txt.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file12_es.txt.json").toPath());

        comparer.compareFiles(fpsA, fpsB);

        List<Map<Cols, String>> tableInfos = WRITER.getTable(ExtractComparer.CONTENTS_TABLE_A);

        Map<Cols, String> row = tableInfos.get(0);
        assertEquals("133", row.get(Cols.CONTENT_LENGTH));
        assertEquals("7", row.get(Cols.NUM_UNIQUE_TOKENS));
        assertEquals("24", row.get(Cols.NUM_TOKENS));
        assertEquals("18", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("108", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("spa", row.get(Cols.COMMON_TOKENS_LANG));
        assertEquals("24", row.get(Cols.NUM_ALPHABETIC_TOKENS));

    }

    @Test
    public void testChinese() throws Exception {
        //make sure that language id matches common words
        //file names.  The test file contains MT'd Simplified Chinese with
        //known "common words" appended at end.

        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file13_attachANotB.doc.json"),
                getResourceAsFile("/test-dirs/extractsA/file13_attachANotB.doc.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("non-existent.json"),
                Paths.get("/test-dirs/extractsB/non-existent.json"));

        comparer.compareFiles(fpsA, fpsB);

        List<Map<Cols, String>> tableInfos = WRITER.getTable(ExtractComparer.CONTENTS_TABLE_A);

        Map<Cols, String> row = tableInfos.get(0);
        assertEquals("122", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("37", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("zho-simp", row.get(Cols.COMMON_TOKENS_LANG));

    }

    @Test
    public void testEmpty() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file1.pdf"),
                getResourceAsFile("/test-dirs/extractsA/file1.pdf.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file1.pdf"),
                getResourceAsFile("/test-dirs/extractsB/file4_emptyB.pdf.json").toPath());
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> table = WRITER.getTable(ExtractComparer.EXTRACT_EXCEPTION_TABLE_B);
        Map<Cols, String> row = table.get(0);
        assertEquals(Integer.toString(ExtractReaderException.TYPE.ZERO_BYTE_EXTRACT_FILE.ordinal()),
                row.get(Cols.EXTRACT_EXCEPTION_ID));
    }


    @Test
    public void testGetContent() throws Exception {
        ContentTags contentTags = new ContentTags("0123456789");
        Map<Cols, String> data = new HashMap<>();
        String content = AbstractProfiler.truncateContent(contentTags, 10, data);
        assertEquals(10, content.length());
        assertEquals("FALSE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));

        content = AbstractProfiler.truncateContent(contentTags, 4, data);
        assertEquals(4, content.length());
        assertEquals("TRUE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));

        //test Metadata with no content
        content = AbstractProfiler.truncateContent(ContentTags.EMPTY_CONTENT_TAGS, 10, data);
        assertEquals(0, content.length());
        assertEquals("FALSE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));

        //test null Metadata
        content = AbstractProfiler.truncateContent(null, 10, data);
        assertEquals(0, content.length());
        assertEquals("FALSE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));
    }

    @Test
    public void testAccessException() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file6_accessEx.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file6_accessEx.pdf.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file6_accessEx.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file6_accessEx.pdf.json").toPath());
        comparer.compareFiles(fpsA, fpsB);
        for (TableInfo t : new TableInfo[]{ExtractComparer.EXCEPTION_TABLE_A,
                ExtractComparer.EXCEPTION_TABLE_B}) {
            List<Map<Cols, String>> table = WRITER.getTable(t);

            Map<Cols, String> rowA = table.get(0);
            assertEquals(
                    Integer.toString(AbstractProfiler.EXCEPTION_TYPE.ACCESS_PERMISSION.ordinal()),
                    rowA.get(Cols.PARSE_EXCEPTION_ID));
            assertNull(rowA.get(Cols.ORIG_STACK_TRACE));
            assertNull(rowA.get(Cols.SORT_STACK_TRACE));
        }
    }

    @Test
    public void testAttachmentCounts() {
        List<Metadata> list = new ArrayList<>();
        Metadata m0 = new Metadata();
        m0.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH,
                "dir1/dir2/file.zip");//bad data should be ignored
        //in the first metadata object
        list.add(m0);
        Metadata m1 = new Metadata();
        m1.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/f1.docx/f2.zip/text1.txt");
        list.add(m1);
        Metadata m2 = new Metadata();
        m2.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/f1.docx/f2.zip/text2.txt");
        list.add(m2);
        Metadata m3 = new Metadata();
        m3.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/f1.docx/f2.zip");
        list.add(m3);
        Metadata m4 = new Metadata();
        m4.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/f1.docx");
        list.add(m4);
        Metadata m5 = new Metadata();
        m5.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/f1.docx/text3.txt");
        list.add(m5);

        List<Integer> counts = AbstractProfiler.countAttachments(list);

        List<Integer> expected = new ArrayList<>();
        expected.add(5);
        expected.add(0);
        expected.add(0);
        expected.add(2);
        expected.add(4);
        expected.add(0);
        assertEquals(expected, counts);
    }

    @Test
    public void testDifferentlyOrderedAttachments() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file14_diffAttachOrder.json"),
                getResourceAsFile("/test-dirs/extractsA/file14_diffAttachOrder.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file6_accessEx.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file14_diffAttachOrder.json").toPath());
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> tableInfos = WRITER.getTable(ExtractComparer.CONTENT_COMPARISONS);
        assertEquals(3, tableInfos.size());
        for (int i = 0; i < tableInfos.size(); i++) {
            assertEquals("problem with " + i, "1.0", tableInfos.get(i).get(Cols.OVERLAP));
        }
    }

    @Test
    public void testTags() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file15_tags.json"),
                getResourceAsFile("/test-dirs/extractsA/file15_tags.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file15_tags.html"),
                getResourceAsFile("/test-dirs/extractsB/file15_tags.html").toPath());
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> tableInfosA = WRITER.getTable(ExtractComparer.TAGS_TABLE_A);
        assertEquals(1, tableInfosA.size());
        Map<Cols, String> tableInfoA = tableInfosA.get(0);
        assertEquals("18", tableInfoA.get(Cols.TAGS_P));
        assertEquals("1", tableInfoA.get(Cols.TAGS_DIV));
        assertEquals("1", tableInfoA.get(Cols.TAGS_TITLE));

        List<Map<Cols, String>> tableInfosB = WRITER.getTable(ExtractComparer.TAGS_TABLE_B);
        assertEquals(1, tableInfosB.size());
        Map<Cols, String> tableInfoB = tableInfosB.get(0);
        assertEquals("18", tableInfoB.get(Cols.TAGS_DIV));
        assertEquals("1", tableInfoB.get(Cols.TAGS_IMG));
    }

    @Test
    public void testBadTags() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file16_badtags.json"),
                getResourceAsFile("/test-dirs/extractsA/file16_badTags.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file16_badtags.html"),
                getResourceAsFile("/test-dirs/extractsB/file16_badTags.html").toPath());
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> tableInfosA = WRITER.getTable(ExtractComparer.TAGS_TABLE_A);
        assertEquals(1, tableInfosA.size());
        Map<Cols, String> tableInfoA = tableInfosA.get(0);
        assertEquals("true", tableInfoA.get(Cols.TAGS_PARSE_EXCEPTION));

        List<Map<Cols, String>> tableInfosB = WRITER.getTable(ExtractComparer.TAGS_TABLE_B);
        assertEquals(1, tableInfosB.size());
        Map<Cols, String> tableInfoB = tableInfosB.get(0);
        //there actually is a tag problem, but tagsoup fixes it.
        //this confirms behavior.
        assertEquals("false", tableInfoB.get(Cols.TAGS_PARSE_EXCEPTION));
    }

    @Test
    public void testTagsOutOfOrder() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file17_tagsOutOfOrder.json"),
                getResourceAsFile("/test-dirs/extractsA/file17_tagsOutOfOrder.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file16_badTags.html"),
                getResourceAsFile("/test-dirs/extractsB/file16_badTags.html").toPath());
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> tableInfosA = WRITER.getTable(ExtractComparer.TAGS_TABLE_A);
        assertEquals(1, tableInfosA.size());
        Map<Cols, String> tableInfoA = tableInfosA.get(0);
        assertEquals("true", tableInfoA.get(Cols.TAGS_PARSE_EXCEPTION));

        //confirm that backoff to html parser worked
        List<Map<Cols, String>> contentsA = WRITER.getTable(ExtractComparer.CONTENTS_TABLE_A);
        assertEquals(1, contentsA.size());
        Map<Cols, String> contentsARow1 = contentsA.get(0);
        String topN = contentsARow1.get(Cols.TOP_N_TOKENS);
        assertNotContained("content:", topN);
        assertNotContained(" p: ", topN);
        assertContains("apache: 12", topN);

    }

    @Test
    @Ignore
    public void testDebug() throws Exception {
        Path commonTokens = Paths.get(getResourceAsFile("/common_tokens_short.txt").toURI());
        AbstractProfiler.loadCommonTokens(commonTokens, "en");
        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file1.pdf.json").toPath());
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file1.pdf.json").toPath());
        comparer.compareFiles(fpsA, fpsB);
        for (TableInfo t : new TableInfo[]{ExtractComparer.COMPARISON_CONTAINERS,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_A,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_B, ExtractComparer.EXCEPTION_TABLE_A,
                ExtractComparer.EXCEPTION_TABLE_B, ExtractComparer.PROFILES_A,
                ExtractComparer.PROFILES_B, ExtractComparer.CONTENTS_TABLE_A,
                ExtractComparer.CONTENTS_TABLE_B, ExtractComparer.CONTENT_COMPARISONS}) {
            //debugPrintTable(t);
        }
    }

    private void debugPrintTable(TableInfo tableInfo) {
        List<Map<Cols, String>> table = WRITER.getTable(tableInfo);
        if (table == null) {
            return;
        }
        int i = 0;
        System.out.println("TABLE: " + tableInfo.getName());
        for (Map<Cols, String> row : table) {
            SortedSet<Cols> keys = new TreeSet<>(row.keySet());
            for (Cols key : keys) {
                System.out.println(i + " :: " + key + " : " + row.get(key));
            }
            i++;
        }
        System.out.println("");
    }

    private void debugPrintRow(Map<Cols, String> row) {
        SortedSet<Cols> keys = new TreeSet<>(row.keySet());
        for (Cols key : keys) {
            System.out.println(key + " : " + row.get(key));
        }
    }

    @Test
    @Ignore("useful for testing 2 files not in test set")
    public void oneOff() throws Exception {
        Path p1 = Paths.get("");
        Path p2 = Paths.get("");

        EvalFilePaths fpsA = new EvalFilePaths(Paths.get("file1.pdf.json"), p1);
        EvalFilePaths fpsB = new EvalFilePaths(Paths.get("file1.pdf.json"), p2);
        comparer.compareFiles(fpsA, fpsB);
        for (TableInfo t : new TableInfo[]{ExtractComparer.COMPARISON_CONTAINERS,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_A,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_B, ExtractComparer.EXCEPTION_TABLE_A,
                ExtractComparer.EXCEPTION_TABLE_B, ExtractComparer.PROFILES_A,
                ExtractComparer.PROFILES_B, ExtractComparer.CONTENTS_TABLE_A,
                ExtractComparer.CONTENTS_TABLE_B, ExtractComparer.CONTENT_COMPARISONS}) {
            debugPrintTable(t);
        }
    }
}
