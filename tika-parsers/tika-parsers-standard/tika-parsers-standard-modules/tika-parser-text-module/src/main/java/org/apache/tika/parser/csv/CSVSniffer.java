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
package org.apache.tika.parser.csv;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

class CSVSniffer {
    static final int EOF = -1;
    static final int NEW_LINE = '\n';
    static final int CARRIAGE_RETURN = '\r';
    private static final int DEFAULT_MARK_LIMIT = 10000;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.50;
    private static final int SPACE = ' ';

    private final Set<Character> delimiters;
    private final int markLimit;
    private final double minConfidence;

    CSVSniffer(Set<Character> delimiters) {
        this(DEFAULT_MARK_LIMIT, delimiters, DEFAULT_MIN_CONFIDENCE);
    }

    CSVSniffer(int markLimit, Set<Character> delimiters, double minConfidence) {
        this.markLimit = markLimit;
        this.delimiters = delimiters;
        this.minConfidence = minConfidence;
    }

    List<CSVResult> sniff(Reader reader) throws IOException {
        if (!reader.markSupported()) {
            reader = new BufferedReader(reader);
        }
        // Every snifflet examines the same window, so read it once into a buffer
        // instead of once per delimiter through a mark/reset + pushback stack.
        // Grown on demand: markLimit is user-configurable and most inputs are
        // far smaller, so an eager char[markLimit] would be waste per parse.
        char[] buf = new char[Math.min(markLimit, 8192)];
        reader.mark(markLimit);
        int len = 0;
        try {
            while (len < markLimit) {
                if (len == buf.length) {
                    buf = Arrays.copyOf(buf, (int) Math.min((long) buf.length * 2, markLimit));
                }
                int n = reader.read(buf, len, buf.length - len);
                if (n <= 0) {
                    break;
                }
                len += n;
            }
        } finally {
            reader.reset();
        }
        boolean hasQuote = false;
        for (int i = 0; i < len; i++) {
            if (buf[i] == '"') {
                hasQuote = true;
                break;
            }
        }
        List<CSVResult> ret = new ArrayList<>();
        for (char delimiter : delimiters) {
            // A window with no delimiter and no quote can only produce single-column
            // rows: consistency and encapsulation are both 0, so the full pass is a
            // foregone conclusion.
            if (!hasQuote && !contains(buf, len, delimiter)) {
                ret.add(new CSVResult(0.0,
                        delimiter == '\t' ? TextAndCSVParser.TSV : TextAndCSVParser.CSV,
                        delimiter));
                continue;
            }
            ret.add(new Snifflet(delimiter, buf, len).sniff());
        }
        Collections.sort(ret);
        return ret;
    }

    private static boolean contains(char[] buf, int len, char c) {
        for (int i = 0; i < len; i++) {
            if (buf[i] == c) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param reader
     * @param metadata
     * @return the best result given the detection results or {@link CSVResult#TEXT}
     * if the confidence is not above a threshold.
     * @throws IOException
     */
    CSVResult getBest(Reader reader, Metadata metadata) throws IOException {
        //TODO: take into consideration the filename.  Perhaps require
        //a higher confidence if detection contradicts filename?
        List<CSVResult> results = sniff(reader);
        if (results == null || results.isEmpty()) {
            return CSVResult.TEXT;
        }
        CSVResult bestResult = results.get(0);
        if (bestResult.getConfidence() < minConfidence) {
            return CSVResult.TEXT;
        }
        // TIKA-4278: colon isn't reliable, e.g. govdocs1/242/242970.txt
        if (results.size() > 1 && bestResult.getDelimiter().equals(':') &&
                Math.abs(results.get(1).getConfidence() - bestResult.getConfidence()) < 0.0001) {
            return results.get(1);
        }
        return bestResult;
    }

    private static class UnsurprisingEOF extends EOFException {

    }

    private static class HitMarkLimitException extends EOFException {

    }

    private static class MutableInt {
        int i;

        MutableInt(int i) {
            this.i = i;
        }

        void increment() {
            i++;
        }

        int intValue() {
            return i;
        }
    }

    //inner class that tests a single hypothesis/combination
    //of parameters for delimiter and quote character
    //this will throw an EOF before reading beyond the
    //markLimit number of characters (not bytes!)
    private class Snifflet {

        private final char delimiter;

        //hardcode this for now
        private final char quoteCharacter = '"';

        // The shared window read once by sniff(Reader); pos is the cursor, so
        // "unread" is a decrement and the mark-limit check is a bounds check.
        private final char[] buf;
        private final int len;
        private int pos = 0;

        Map<Integer, MutableInt> rowLengthCounts = new HashMap<>();
        int colCount = 0;
        boolean rowZero = true;
        boolean rowZeroEmpty = false;
        int encapsulated = 0; //number of cells that are encapsulated in dquotes (for now)
        boolean parseException = false;
        // Cell content is never analyzed (see the unquoted() TODO that was here);
        // only whether the current cell is non-empty matters.
        private int unquotedLen = 0;

        public Snifflet(char delimiter, char[] buf, int len) {
            this.delimiter = delimiter;
            this.buf = buf;
            this.len = len;
        }

        CSVResult sniff() throws IOException {
            boolean eof = false;
            boolean hitMarkLimit = false;
            int lastC = -1;
            try {
                int c = read();
                while (c != EOF) {
                    if (c == quoteCharacter) {
                        unquotedLen = 0;
                        //test to make sure there isn't an unencapsulated quote character
                        // in the middle of a cell
                        if (lastC > -1 && lastC != delimiter && lastC != NEW_LINE &&
                                lastC != CARRIAGE_RETURN) {
                            parseException = true;
                            return calcResult();
                        }
                        //TODO: test to make sure cell doesn't start with escaped
                        // ""the quick brown cat"
                        boolean correctlyEncapsulated = consumeQuoted(quoteCharacter);
                        if (!correctlyEncapsulated) {
                            parseException = true;
                            return calcResult();
                        }
                    } else if (c == delimiter) {
                        unquotedLen = 0;
                        endColumn();
                        consumeSpaceCharacters();
                    } else if (c == NEW_LINE || c == CARRIAGE_RETURN) {
                        if (unquotedLen > 0) {
                            endColumn();
                        }
                        unquotedLen = 0;
                        endRow();
                        consumeNewLines();
                    } else {
                        unquotedLen++;
                    }
                    lastC = c;
                    c = read();
                }
            } catch (HitMarkLimitException e) {
                hitMarkLimit = true;
            } catch (UnsurprisingEOF e) {
                //totally ignore
            } catch (EOFException e) {
                //the consume* throw this to avoid
                //having to check -1 every time
                eof = true;
            }
            //if you've hit the marklimit or an eof on a truncated file
            //don't add the last row's info
            if (!hitMarkLimit && !eof && lastC != NEW_LINE && lastC != CARRIAGE_RETURN) {
                unquotedLen = 0;
                endColumn();
                endRow();
            }
            return calcResult();
        }

        private CSVResult calcResult() {
            double confidence = getConfidence();
            MediaType mediaType = TextAndCSVParser.CSV;
            if (delimiter == '\t') {
                mediaType = TextAndCSVParser.TSV;
            }
            return new CSVResult(confidence, mediaType, delimiter);
        }

        void consumeSpaceCharacters() throws IOException {
            int c = read();
            while (c == SPACE) {
                c = read();
            }
            if (c == EOF) {
                throw new UnsurprisingEOF();
            }
            unread(c);
        }


        /**
         * @param reader
         * @param quoteCharacter
         * @return whether or not this was a correctly encapsulated cell
         * @throws UnsurprisingEOF if the file ended immediately after the close quote
         * @throws EOFException    if the file ended in the middle of the encapsulated section
         * @throws IOException     on other IOExceptions
         */
        boolean consumeQuoted(int quoteCharacter) throws IOException {
            //this currently assumes excel "escaping" of double quotes:
            //'the " quick' -> "the "" quick"
            //we can make this more interesting later with other
            //escaping options
            int c = read();
            while (c != -1) {
                if (c == quoteCharacter) {
                    int nextC = read();
                    if (nextC == EOF) {
                        encapsulated++;
                        endColumn();
                        throw new UnsurprisingEOF();
                    } else if (nextC != quoteCharacter) {
                        encapsulated++;
                        endColumn();
                        unread(nextC);
                        consumeSpaceCharacters();
                        //now make sure that the next character is eof, \r\n
                        //or a delimiter
                        nextC = read();
                        if (nextC == EOF) {
                            throw new UnsurprisingEOF();
                        } else if (nextC == NEW_LINE || nextC == CARRIAGE_RETURN) {
                            unread(nextC);
                            return true;
                        } else if (nextC != delimiter) {
                            unread(nextC);
                            return false;
                        }
                        unread(nextC);
                        return true;
                    }
                }
                c = read();
            }
            throw new EOFException();
        }

        private int read() throws IOException {
            // pos tracks chars consumed exactly as charsRead did (unread decrements
            // both), so the original off-by-one mark-limit semantics are preserved.
            if (pos >= markLimit - 1) {
                throw new HitMarkLimitException();
            }
            if (pos >= len) {
                return EOF;
            }
            return buf[pos++];
        }

        private void unread(int c) {
            if (c != EOF) {
                pos--;
            }
        }

        //consume all consecutive '\r\n' in any order
        void consumeNewLines() throws IOException {
            int c = read();
            while (c == NEW_LINE || c == CARRIAGE_RETURN) {
                c = read();
            }
            if (c == EOF) {
                throw new EOFException();
            }
            unread(c);
        }


        void endColumn() {
            colCount++;
        }

        void endRow() {
            MutableInt cnt = rowLengthCounts.get(colCount);
            if (cnt == null) {
                cnt = new MutableInt(1);
                rowLengthCounts.put(colCount, cnt);
            } else {
                cnt.increment();
            }
            if (rowZero && colCount <= 1) {
                // row zero single column => no delimiter in first line
                rowZeroEmpty = true;
            }
            colCount = 0;
            rowZero = false;
        }

        double getConfidence() {
            double confidence = 0.0f;

            if (parseException) {
                return -1.0f;
            }
            //TODO -- add tests for long tokens containing
            //other delimiters, e.g. the,quick,brown,fox as a token
            //when testing '\t'
            double colCountConsistencyConf = calculateColumnCountConsistency();
            if (colCountConsistencyConf > -1.0) {
                confidence = colCountConsistencyConf;
            }
            //the idea is that if there are a bunch of encapsulated
            //cells, then that should outweigh column length inconsistency
            //this particular formula offers a small initial increase
            //that eventually approaches 1.0
            double encapsulatedBonus = 0;
            if (encapsulated > 0) {
                encapsulatedBonus = 1.0 - (1.0d / Math.pow(encapsulated, 0.2));
            }
            return Math.min(confidence + encapsulatedBonus, 1.0);
        }

        private double calculateColumnCountConsistency() {
            int max = -1;
            int totalRows = 0;
            //find the most common row
            for (Map.Entry<Integer, MutableInt> e : rowLengthCounts.entrySet()) {
                int numCols = e.getKey();
                int count = e.getValue().intValue();
                //require that numCols > 1 so that you had at least
                //one delimiter in that row
                if (numCols > 1 && count > max) {
                    max = count;
                }
                totalRows += count;
            }
            //if there's not enough info
            if (max < 0 || totalRows < 3) {
                return 0.0;
            }

            if (rowZeroEmpty) {
                // TIKA-4278: not credible that there would be no delimiter in row zero
                return 0.0;
            }

            //TODO: convert this to continuous vs vague heuristic step function
            double consistency = (double) max / (double) totalRows;
            return ((1d - (1d / Math.pow(totalRows, 0.3))) * consistency);
        }

    }

}
