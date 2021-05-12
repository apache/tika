/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.ProxyReader;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

class CSVSniffer {
    static final int EOF = -1;
    static final int NEW_LINE = '\n';
    static final int CARRIAGE_RETURN = '\r';
    private static final int DEFAULT_MARK_LIMIT = 10000;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.50;
    private static final int PUSH_BACK = 2;
    private static final int SPACE = ' ';

    private final char[] delimiters;
    private final int markLimit;
    private final double minConfidence;

    CSVSniffer(char[] delimiters) {
        this(DEFAULT_MARK_LIMIT, delimiters, DEFAULT_MIN_CONFIDENCE);
    }

    CSVSniffer(int markLimit, char[] delimiters, double minConfidence) {
        this.markLimit = markLimit;
        this.delimiters = delimiters;
        this.minConfidence = minConfidence;
    }

    List<CSVResult> sniff(Reader reader) throws IOException {
        if (!reader.markSupported()) {
            reader = new BufferedReader(reader);
        }
        List<CSVResult> ret = new ArrayList<>();
        for (char delimiter : delimiters) {
            reader.mark(markLimit);
            try {
                CSVResult result = new Snifflet(delimiter).sniff(reader);
                ret.add(result);
            } finally {
                reader.reset();
            }
        }
        Collections.sort(ret);
        return ret;
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
        if (results == null || results.size() == 0) {
            return CSVResult.TEXT;
        }
        CSVResult bestResult = results.get(0);
        if (bestResult.getConfidence() < minConfidence) {
            return CSVResult.TEXT;
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

        Map<Integer, MutableInt> rowLengthCounts = new HashMap<>();
        int charsRead = 0;
        int colCount = 0;
        int encapsulated = 0; //number of cells that are encapsulated in dquotes (for now)
        boolean parseException = false;

        public Snifflet(char delimiter) {
            this.delimiter = delimiter;
        }

        CSVResult sniff(Reader r) throws IOException {
            boolean eof = false;
            boolean hitMarkLimit = false;
            int lastC = -1;
            StringBuilder unquoted = new StringBuilder();
            try (PushbackReader reader = new PushbackReader(new CloseShieldReader(r), PUSH_BACK)) {
                int c = read(reader);
                while (c != EOF) {
                    if (c == quoteCharacter) {
                        handleUnquoted(unquoted);
                        //test to make sure there isn't an unencapsulated quote character
                        // in the middle of a cell
                        if (lastC > -1 && lastC != delimiter && lastC != NEW_LINE &&
                                lastC != CARRIAGE_RETURN) {
                            parseException = true;
                            return calcResult();
                        }
                        //TODO: test to make sure cell doesn't start with escaped
                        // ""the quick brown cat"
                        boolean correctlyEncapsulated = consumeQuoted(reader, quoteCharacter);
                        if (!correctlyEncapsulated) {
                            parseException = true;
                            return calcResult();
                        }
                    } else if (c == delimiter) {
                        handleUnquoted(unquoted);
                        endColumn();
                        consumeSpaceCharacters(reader);
                    } else if (c == NEW_LINE || c == CARRIAGE_RETURN) {
                        if (unquoted.length() > 0) {
                            endColumn();
                        }
                        handleUnquoted(unquoted);
                        endRow();
                        consumeNewLines(reader);
                    } else {
                        unquoted.append((char) c);
                    }
                    lastC = c;
                    c = read(reader);
                }
            } catch (HitMarkLimitException e) {
                hitMarkLimit = true;
            } catch (UnsurprisingEOF e) {
                //totally ignore
            } catch (EOFException e) {
                //the consume* throw this to avoid
                //having to check -1 every time and
                //having to rely on potentially wonky
                //inputstreams not consistently returning -1
                //after hitting EOF and returning the first -1.
                //Yes.  That's a thing.
                eof = true;
            } finally {
                r.reset();
            }
            //if you've hit the marklimit or an eof on a truncated file
            //don't add the last row's info
            if (!hitMarkLimit && !eof && lastC != NEW_LINE && lastC != CARRIAGE_RETURN) {
                handleUnquoted(unquoted);
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

        private void handleUnquoted(StringBuilder unquoted) {
            if (unquoted.length() > 0) {
                unquoted(unquoted.toString());
                unquoted.setLength(0);
            }
        }

        void consumeSpaceCharacters(PushbackReader reader) throws IOException {
            int c = read(reader);
            while (c == SPACE) {
                c = read(reader);
            }
            if (c == EOF) {
                throw new UnsurprisingEOF();
            }
            unread(reader, c);
        }


        /**
         * @param reader
         * @param quoteCharacter
         * @return whether or not this was a correctly encapsulated cell
         * @throws UnsurprisingEOF if the file ended immediately after the close quote
         * @throws EOFException    if the file ended in the middle of the encapsulated section
         * @throws IOException     on other IOExceptions
         */
        boolean consumeQuoted(PushbackReader reader, int quoteCharacter) throws IOException {
            //this currently assumes excel "escaping" of double quotes:
            //'the " quick' -> "the "" quick"
            //we can make this more interesting later with other
            //escaping options
            int c = read(reader);
            while (c != -1) {
                if (c == quoteCharacter) {
                    int nextC = read(reader);
                    if (nextC == EOF) {
                        encapsulated++;
                        endColumn();
                        throw new UnsurprisingEOF();
                    } else if (nextC != quoteCharacter) {
                        encapsulated++;
                        endColumn();
                        unread(reader, nextC);
                        consumeSpaceCharacters(reader);
                        //now make sure that the next character is eof, \r\n
                        //or a delimiter
                        nextC = read(reader);
                        if (nextC == EOF) {
                            throw new UnsurprisingEOF();
                        } else if (nextC == NEW_LINE || nextC == CARRIAGE_RETURN) {
                            unread(reader, nextC);
                            return true;
                        } else if (nextC != delimiter) {
                            unread(reader, nextC);
                            return false;
                        }
                        unread(reader, nextC);
                        return true;
                    }
                }
                c = read(reader);
            }
            throw new EOFException();
        }

        private int read(PushbackReader reader) throws IOException {
            if (charsRead >= markLimit - 1) {
                throw new HitMarkLimitException();
            }
            int c = reader.read();
            if (c == EOF) {
                return EOF;
            }
            charsRead++;
            return c;
        }

        private void unread(PushbackReader reader, int c) throws IOException {
            if (c != EOF) {
                reader.unread(c);
                charsRead--;
            }
        }

        //consume all consecutive '\r\n' in any order
        void consumeNewLines(PushbackReader reader) throws IOException {
            int c = read(reader);
            while (c == NEW_LINE || c == CARRIAGE_RETURN) {
                c = read(reader);
            }
            if (c == EOF) {
                throw new EOFException();
            }
            unread(reader, c);
            return;
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
            colCount = 0;
        }

        void unquoted(String string) {
            //TODO -- do some analysis to make sure you don't have
            //large tokens like 2,3,2,3,2,3,
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

            //TODO: convert this to continuous vs vague heuristic step function
            double consistency = (double) max / (double) totalRows;
            return ((1d - (1d / Math.pow(totalRows, 0.3))) * consistency);
        }

    }

    private static class CloseShieldReader extends ProxyReader {
        public CloseShieldReader(Reader r) {
            super(r);
        }

        @Override
        public void close() throws IOException {
            //do nothing
        }
    }
}
