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

import static org.apache.tika.eval.AbstractProfiler.NON_EXISTENT_FILE_LENGTH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple struct to keep track of relative path of source file (
 * original binary file, e.g. /subdir/document1.doc)
 * and the extract file (e.g. /subdir/document1.doc.json).
 */
class EvalFilePaths {

    private final Path relativeSourceFilePath;
    private final Path extractFile;

    private long sourceFileLength = NON_EXISTENT_FILE_LENGTH;
    private long extractFileLength = NON_EXISTENT_FILE_LENGTH;


    public EvalFilePaths(Path relativeSourceFilePath, Path extractFile, long srcFileLen) {
        this(relativeSourceFilePath, extractFile);
        this.sourceFileLength = srcFileLen;
    }

    public EvalFilePaths(Path relativeSourceFilePath, Path extractFile) {
        if (extractFile != null && Files.isRegularFile(extractFile)) {
            try {
                extractFileLength = Files.size(extractFile);
            } catch (IOException e) {
                //swallow ?
            }
        }
        this.relativeSourceFilePath = relativeSourceFilePath;
        this.extractFile = extractFile;
    }

    public Path getRelativeSourceFilePath() {
        return relativeSourceFilePath;
    }

    //this path may or may not exist and it could be null!
    public Path getExtractFile() {
        return extractFile;
    }

    //if it doesn't exist, it'll be -1l.
    public long getSourceFileLength() {
        return sourceFileLength;
    }

    public long getExtractFileLength() {
        return extractFileLength;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EvalFilePaths that = (EvalFilePaths) o;

        if (sourceFileLength != that.sourceFileLength) return false;
        if (extractFileLength != that.extractFileLength) return false;
        if (relativeSourceFilePath != null ? !relativeSourceFilePath.equals(that.relativeSourceFilePath) : that.relativeSourceFilePath != null)
            return false;
        return extractFile != null ? extractFile.equals(that.extractFile) : that.extractFile == null;

    }

    @Override
    public int hashCode() {
        int result = relativeSourceFilePath != null ? relativeSourceFilePath.hashCode() : 0;
        result = 31 * result + (extractFile != null ? extractFile.hashCode() : 0);
        result = 31 * result + (int) (sourceFileLength ^ (sourceFileLength >>> 32));
        result = 31 * result + (int) (extractFileLength ^ (extractFileLength >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "EvalFilePaths{" +
                "relativeSourceFilePath=" + relativeSourceFilePath +
                ", extractFile=" + extractFile +
                ", sourceFileLength=" + sourceFileLength +
                ", extractFileLength=" + extractFileLength +
                '}';
    }
}
