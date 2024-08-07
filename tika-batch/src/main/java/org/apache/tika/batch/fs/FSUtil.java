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
package org.apache.tika.batch.fs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle some common issues when
 * reading from and writing to a file system (FS).
 */
public class FSUtil {

    private final static Pattern FILE_NAME_PATTERN = Pattern.compile("\\A(.*?)(?:\\((\\d+)\\))?\\.([^\\.]+)\\Z");

    @Deprecated
    public static boolean checkThisIsAncestorOfThat(File ancestor, File child) {
        int ancLen = ancestor
                .getAbsolutePath()
                .length();
        int childLen = child
                .getAbsolutePath()
                .length();
        if (childLen <= ancLen) {
            return false;
        }

        String childBase = child
                .getAbsolutePath()
                .substring(0, ancLen);
        return childBase.equals(ancestor.getAbsolutePath());

    }

    @Deprecated
    public static boolean checkThisIsAncestorOfOrSameAsThat(File ancestor, File child) {
        if (ancestor.equals(child)) {
            return true;
        }
        return checkThisIsAncestorOfThat(ancestor, child);
    }

    /**
     * Given an output root and an initial relative path,
     * return the output file according to the HANDLE_EXISTING strategy
     * <p/>
     * In the most basic use case, given a root directory "input",
     * a file's relative path "dir1/dir2/fileA.docx", and an output directory
     * "output", the output file would be "output/dir1/dir2/fileA.docx."
     * <p/>
     * If HANDLE_EXISTING is set to OVERWRITE, this will not check to see if the output already exists,
     * and the returned file could overwrite an existing file!!!
     * <p/>
     * If HANDLE_EXISTING is set to RENAME, this will try to increment a counter at the end of
     * the file name (fileA(2).docx) until there is a file name that doesn't exist.
     * <p/>
     * This will return null if handleExisting == HANDLE_EXISTING.SKIP and
     * the candidate file already exists.
     * <p/>
     * This will throw an IOException if HANDLE_EXISTING is set to
     * RENAME, and a candidate cannot output file cannot be found
     * after trying to increment the file count (e.g. fileA(2).docx) 10000 times
     * and then after trying 20,000 UUIDs.
     *
     * @param outputRoot          directory root for output
     * @param initialRelativePath initial relative path (including file name, which may be renamed)
     * @param handleExisting      what to do if the output file exists
     * @param suffix              suffix to add to files, can be null
     * @return output file or null if no output file should be created
     * @throws java.io.IOException
     * @see #getOutputPath(Path, String, HANDLE_EXISTING, String)
     */
    @Deprecated
    public static File getOutputFile(File outputRoot, String initialRelativePath, HANDLE_EXISTING handleExisting, String suffix) throws IOException {
        return getOutputPath(Paths.get(outputRoot.toURI()), initialRelativePath, handleExisting, suffix).toFile();
    }

    /**
     * Given an output root and an initial relative path,
     * return the output file according to the HANDLE_EXISTING strategy
     * <p/>
     * In the most basic use case, given a root directory "input",
     * a file's relative path "dir1/dir2/fileA.docx", and an output directory
     * "output", the output file would be "output/dir1/dir2/fileA.docx."
     * <p/>
     * If HANDLE_EXISTING is set to OVERWRITE, this will not check to see if the output already exists,
     * and the returned file could overwrite an existing file!!!
     * <p/>
     * If HANDLE_EXISTING is set to RENAME, this will try to increment a counter at the end of
     * the file name (fileA(2).docx) until there is a file name that doesn't exist.
     * <p/>
     * This will return null if handleExisting == HANDLE_EXISTING.SKIP and
     * the candidate file already exists.
     * <p/>
     * This will throw an IOException if HANDLE_EXISTING is set to
     * RENAME, and a candidate cannot output file cannot be found
     * after trying to increment the file count (e.g. fileA(2).docx) 10000 times
     * and then after trying 20,000 UUIDs.
     *
     * @param outputRoot          root directory into which to put the path
     * @param initialRelativePath relative path including file ("somedir/subdir1/file.doc")
     * @param handleExisting      policy for what to do if the output path already exists
     * @param suffix              suffix to add to the output path
     * @return can return null
     * @throws IOException
     */
    public static Path getOutputPath(Path outputRoot, String initialRelativePath, HANDLE_EXISTING handleExisting, String suffix) throws IOException {

        String localSuffix = (suffix == null) ? "" : suffix;
        Path cand = FSUtil.resolveRelative(outputRoot, initialRelativePath + "." + localSuffix);
        if (Files.exists(cand)) {
            if (handleExisting.equals(HANDLE_EXISTING.OVERWRITE)) {
                return cand;
            } else if (handleExisting.equals(HANDLE_EXISTING.SKIP)) {
                return null;
            }
        }

        //if we're here, the output file exists, and
        //we must find a new name for it.

        //groups for "testfile(1).txt":
        //group(1) is "testfile"
        //group(2) is 1
        //group(3) is "txt"
        //Note: group(2) can be null
        int cnt = 0;
        String fNameBase = null;
        String fNameExt = "";
        //this doesn't include the addition of the localSuffix
        Path candOnly = FSUtil.resolveRelative(outputRoot, initialRelativePath);
        Matcher m = FILE_NAME_PATTERN.matcher(candOnly
                .getFileName()
                .toString());
        if (m.find()) {
            fNameBase = m.group(1);

            if (m.group(2) != null) {
                try {
                    cnt = Integer.parseInt(m.group(2));
                } catch (NumberFormatException e) {
                    //swallow
                }
            }
            if (m.group(3) != null) {
                fNameExt = m.group(3);
            }
        }

        Path outputParent = cand.getParent();
        while (fNameBase != null && Files.exists(cand) && ++cnt < 10000) {
            String candFileName = fNameBase + "(" + cnt + ")." + fNameExt + "" + localSuffix;
            cand = FSUtil.resolveRelative(outputParent, candFileName);
        }
        //reset count to 0 and try 20000 times
        cnt = 0;
        while (Files.exists(cand) && cnt++ < 20000) {
            UUID uid = UUID.randomUUID();
            cand = FSUtil.resolveRelative(outputParent, uid.toString() + fNameExt + "" + localSuffix);
        }

        if (Files.exists(cand)) {
            throw new IOException("Couldn't find candidate output file after trying " + "very, very hard");
        }
        return cand;
    }

    /**
     * Convenience method to ensure that "other" is not an absolute path.
     * One could imagine malicious use of this.
     *
     * @param p
     * @param other
     * @return resolved path
     * @throws IllegalArgumentException if "other" is an absolute path
     */
    public static Path resolveRelative(Path p, String other) {
        Path op = Paths.get(other);
        if (op.isAbsolute()) {
            throw new IllegalArgumentException(other + " cannot be an absolute path!");
        }
        return p.resolve(op);
    }

    public enum HANDLE_EXISTING {
        OVERWRITE, RENAME, SKIP
    }
}
