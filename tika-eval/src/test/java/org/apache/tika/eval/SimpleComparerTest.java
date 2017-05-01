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

import static org.apache.tika.eval.AbstractProfiler.EXCEPTION_TYPE;
import static org.apache.tika.eval.AbstractProfiler.getContent;
import static org.apache.tika.eval.io.ExtractReader.IGNORE_LENGTH;
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

import org.apache.tika.MockDBWriter;
import org.apache.tika.TikaTest;
import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.ExtractReader;
import org.apache.tika.eval.io.ExtractReaderException;
import org.apache.tika.eval.util.LanguageIDWrapper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

//These tests ensure that the comparer is extracting the right information
//into a Map<String,String>.  A full integration test
//should also ensure that the elements are properly being written to the db

public class SimpleComparerTest extends TikaTest {

    private ExtractComparer comparer = null;
    private MockDBWriter writer = null;

    @Before
    public void setUp() throws Exception {
        writer = new MockDBWriter();
        comparer = new ExtractComparer(null, null,
                Paths.get("extractsA"), Paths.get("extractsB"),
                new ExtractReader(ExtractReader.ALTER_METADATA_LIST.AS_IS,
                        IGNORE_LENGTH, IGNORE_LENGTH),
                writer);
        AbstractProfiler.loadCommonTokens(this.getResourceAsFile("/common_tokens").toPath(), "en");
        LanguageIDWrapper.loadBuiltInModels();
    }

    @Test
    public void testBasic() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file1.pdf.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file1.pdf.json").toPath());

        comparer.compareFiles(fpsA, fpsB);

        List<Map<Cols, String>> tableInfos = writer.getTable(ExtractComparer.CONTENT_COMPARISONS);
        Map<Cols, String> row = tableInfos.get(0);
        assertEquals("0", row.get(Cols.ID));
        assertTrue(
                row.get(Cols.TOP_10_UNIQUE_TOKEN_DIFFS_A)
                        .startsWith("1,200: 1 | 120000: 1 | over: 1"));

        tableInfos = writer.getTable(ExtractComparer.CONTENTS_TABLE_A);
        row = tableInfos.get(0);
        assertEquals("0", row.get(Cols.ID));
        assertEquals("70", row.get(Cols.CONTENT_LENGTH));
        assertEquals("10", row.get(Cols.NUM_UNIQUE_TOKENS));
        assertEquals("14", row.get(Cols.NUM_TOKENS));
        assertEquals("12", row.get(Cols.NUM_ALPHABETIC_TOKENS));
        assertEquals("6", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("57", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("en", row.get(Cols.COMMON_TOKENS_LANG));

        tableInfos = writer.getTable(ExtractComparer.CONTENTS_TABLE_B);
        row = tableInfos.get(0);
        assertEquals("0", row.get(Cols.ID));
        assertEquals("76", row.get(Cols.CONTENT_LENGTH));
        assertEquals("9", row.get(Cols.NUM_UNIQUE_TOKENS));
        assertEquals("13", row.get(Cols.NUM_TOKENS));
        assertEquals("4", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("64", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("en", row.get(Cols.COMMON_TOKENS_LANG));

        tableInfos = writer.getTable(ExtractComparer.PROFILES_A);
        row = tableInfos.get(0);
        assertEquals("2", row.get(Cols.NUM_PAGES));

    }

    @Test
    public void testBasicSpanish() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file12_es.txt.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file12_es.txt.json").toPath());

        comparer.compareFiles(fpsA, fpsB);

        List<Map<Cols, String>> tableInfos = writer.getTable(ExtractComparer.CONTENTS_TABLE_A);

        Map<Cols, String> row = tableInfos.get(0);
        assertEquals("133", row.get(Cols.CONTENT_LENGTH));
        assertEquals("7", row.get(Cols.NUM_UNIQUE_TOKENS));
        assertEquals("24", row.get(Cols.NUM_TOKENS));
        assertEquals("3", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("108", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("es", row.get(Cols.COMMON_TOKENS_LANG));
        assertEquals("24", row.get(Cols.NUM_ALPHABETIC_TOKENS));

    }

    @Test
    public void testChinese() throws Exception {
        //make sure that language id matches common words
        //file names.  The test file contains MT'd Simplified Chinese with
        //known "common words" appended at end.

        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file13_attachANotB.doc.json"),
                getResourceAsFile("/test-dirs/extractsA/file13_attachANotB.doc.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("non-existent.json"),
                getResourceAsFile("/test-dirs/extractsB/non-existent.json").toPath());

        comparer.compareFiles(fpsA, fpsB);

        List<Map<Cols, String>> tableInfos = writer.getTable(ExtractComparer.CONTENTS_TABLE_A);

        Map<Cols, String> row = tableInfos.get(0);
        assertEquals("122", row.get(Cols.TOKEN_LENGTH_SUM));
        assertEquals("3", row.get(Cols.NUM_COMMON_TOKENS));
        assertEquals("zh-cn", row.get(Cols.COMMON_TOKENS_LANG));

    }

    @Test
    public void testEmpty() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file1.pdf"),
                getResourceAsFile("/test-dirs/extractsA/file1.pdf.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file1.pdf"),
                getResourceAsFile("/test-dirs/extractsB/file4_emptyB.pdf.json").toPath()
        );
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> table = writer.getTable(ExtractComparer.EXTRACT_EXCEPTION_TABLE_B);
        Map<Cols, String> row = table.get(0);
        //debugPrintRow(row);
        assertEquals(Integer.toString(ExtractReaderException.TYPE.ZERO_BYTE_EXTRACT_FILE.ordinal()),
                row.get(Cols.EXTRACT_EXCEPTION_ID));
    }


    @Test
    public void testGetContent() throws Exception {
        Metadata m = new Metadata();
        m.add(RecursiveParserWrapper.TIKA_CONTENT, "0123456789");
        Map<Cols, String> data = new HashMap<>();
        String content = getContent(m, 10, data);
        assertEquals(10, content.length());
        assertEquals("FALSE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));

        content = getContent(m, 4, data);
        assertEquals(4, content.length());
        assertEquals("TRUE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));

        //test Metadata with no content
        content = getContent(new Metadata(), 10, data);
        assertEquals(0, content.length());
        assertEquals("FALSE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));

        //test null Metadata
        content = getContent(null, 10, data);
        assertEquals(0, content.length());
        assertEquals("FALSE", data.get(Cols.CONTENT_TRUNCATED_AT_MAX_LEN));
    }

    @Test
    public void testAccessException() throws Exception {
        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file6_accessEx.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file6_accessEx.pdf.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file6_accessEx.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file6_accessEx.pdf.json").toPath()
        );
        comparer.compareFiles(fpsA, fpsB);
        for (TableInfo t : new TableInfo[]{ExtractComparer.EXCEPTION_TABLE_A, ExtractComparer.EXCEPTION_TABLE_B}) {
            List<Map<Cols, String>> table = writer.getTable(t);

            Map<Cols, String> rowA = table.get(0);
            //debugPrintRow(rowA);
            assertEquals(Integer.toString(EXCEPTION_TYPE.ACCESS_PERMISSION.ordinal()),
                    rowA.get(Cols.PARSE_EXCEPTION_ID));
            assertNull(rowA.get(Cols.ORIG_STACK_TRACE));
            assertNull(rowA.get(Cols.SORT_STACK_TRACE));
        }
    }


    @Test
    public void testAttachmentCounts() {
        List<Metadata> list = new ArrayList<>();
        Metadata m0 = new Metadata();
        m0.set(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH, "dir1/dir2/file.zip");//bad data should be ignored
                                                                                    //in the first metadata object
        list.add(m0);
        Metadata m1 = new Metadata();
        m1.set(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH, "/f1.docx/f2.zip/text1.txt");
        list.add(m1);
        Metadata m2 = new Metadata();
        m2.set(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH, "/f1.docx/f2.zip/text2.txt");
        list.add(m2);
        Metadata m3 = new Metadata();
        m3.set(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH, "/f1.docx/f2.zip");
        list.add(m3);
        Metadata m4 = new Metadata();
        m4.set(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH, "/f1.docx");
        list.add(m4);
        Metadata m5 = new Metadata();
        m5.set(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH, "/f1.docx/text3.txt");
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
        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file14_diffAttachOrder.json"),
                getResourceAsFile("/test-dirs/extractsA/file14_diffAttachOrder.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file6_accessEx.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file14_diffAttachOrder.json").toPath()
        );
        comparer.compareFiles(fpsA, fpsB);
        List<Map<Cols, String>> tableInfos = writer.getTable(ExtractComparer.CONTENT_COMPARISONS);
        assertEquals(3, tableInfos.size());
        for (int i = 0; i < tableInfos.size(); i++) {
            assertEquals("1.0", tableInfos.get(i).get(Cols.OVERLAP));
        }
    }

    @Test
    @Ignore
    public void testDebug() throws Exception {
        Path commonTokens = Paths.get(getResourceAsFile("/common_tokens_short.txt").toURI());
        AbstractProfiler.loadCommonTokens(commonTokens, "en");
        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsA/file1.pdf.json").toPath()
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                getResourceAsFile("/test-dirs/extractsB/file1.pdf.json").toPath()
        );
        comparer.compareFiles(fpsA, fpsB);
        for (TableInfo t : new TableInfo[]{
                ExtractComparer.COMPARISON_CONTAINERS,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_A,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_B,
                ExtractComparer.EXCEPTION_TABLE_A,
                ExtractComparer.EXCEPTION_TABLE_B,
                ExtractComparer.PROFILES_A,
                ExtractComparer.PROFILES_B,
                ExtractComparer.CONTENTS_TABLE_A,
                ExtractComparer.CONTENTS_TABLE_B,
                ExtractComparer.CONTENT_COMPARISONS}) {
            //debugPrintTable(t);
        }
    }

    private void debugPrintTable(TableInfo tableInfo) {
        List<Map<Cols, String>> table = writer.getTable(tableInfo);
        if (table == null) {
            return;
        }
        int i = 0;
        System.out.println("TABLE: "+tableInfo.getName());
        for (Map<Cols, String> row : table) {
            SortedSet<Cols> keys = new TreeSet<Cols>(row.keySet());
            for (Cols key : keys) {
                System.out.println( i + " :: " + key + " : " + row.get(key));
            }
            i++;
        }
        System.out.println("");
    }

    private void debugPrintRow(Map<Cols, String> row) {
        SortedSet<Cols> keys = new TreeSet<Cols>(row.keySet());
        for (Cols key : keys) {
            System.out.println(key + " : " + row.get(key));
        }
    }

    @Test
    @Ignore("useful for testing 2 files not in test set")
    public void oneOff() throws Exception {
        Path p1 = Paths.get("");
        Path p2 = Paths.get("");

        EvalFilePaths fpsA = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                p1
        );
        EvalFilePaths fpsB = new EvalFilePaths(
                Paths.get("file1.pdf.json"),
                p2
        );
        comparer.compareFiles(fpsA, fpsB);
        for (TableInfo t : new TableInfo[]{
                ExtractComparer.COMPARISON_CONTAINERS,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_A,
                ExtractComparer.EXTRACT_EXCEPTION_TABLE_B,
                ExtractComparer.EXCEPTION_TABLE_A,
                ExtractComparer.EXCEPTION_TABLE_B,
                ExtractComparer.PROFILES_A,
                ExtractComparer.PROFILES_B,
                ExtractComparer.CONTENTS_TABLE_A,
                ExtractComparer.CONTENTS_TABLE_B,
                ExtractComparer.CONTENT_COMPARISONS}) {
            debugPrintTable(t);
        }

    }
}
