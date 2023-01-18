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

package org.apache.tika.parser.dwg;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.utils.StringUtils;

public class DWGParserConfig implements Serializable {

    private static final long serialVersionUID = -7623524257255755725L;
    private String dwgReadExecutable = "";
    private boolean cleanDwgReadOutput = true;
    private int cleanDwgReadOutputBatchSize = 10000000;
    // default to 5 minutes, some large DWG's do take a while...
    private long dwgReadTimeout = 300000;
    // we need to remove non UTF chars and Nan's (dwgread outputs these as nan)
    private String cleanDwgReadRegexToReplace = "[^\\x20-\\x7e]";
    private String cleanDwgReadReplaceWith = "";
    @SuppressWarnings("unused") 
    private boolean hasDwgRead;
    private static final Logger LOG = LoggerFactory.getLogger(DWGParserConfig.class);

    public void initialize(Map<String, Param> params) throws TikaConfigException {
        hasDwgRead = hasDwgRead();

    }

    public boolean hasDwgRead() throws TikaConfigException {
        // Fetch where the config says to find DWGRead
        String dwgRead = getDwgReadExecutable();

        if (!StringUtils.isBlank(dwgRead) && !Files.isRegularFile(Paths.get(dwgRead))) {
            throw new TikaConfigException("DwgRead cannot be found at: " + dwgRead);
        }

        // Try running DWGRead from there, and see if it exists + works
        String[] checkCmd = { dwgRead };
        boolean hasDwgRead = ExternalParser.check(checkCmd);
        LOG.debug("hasDwgRead (path: " + Arrays.toString(checkCmd) + "): " + hasDwgRead);
        return hasDwgRead;
    }

    public String getDwgReadExecutable() {

        return dwgReadExecutable;
    }

    public boolean isCleanDwgReadOutput() {
        return cleanDwgReadOutput;
    }

    public int getCleanDwgReadOutputBatchSize() {
        return cleanDwgReadOutputBatchSize;
    }

    public long getDwgReadTimeout() {
        return dwgReadTimeout;
    }

    public String getCleanDwgReadRegexToReplace() {
        return cleanDwgReadRegexToReplace;
    }

    public String getCleanDwgReadReplaceWith() {
        return cleanDwgReadReplaceWith;
    }

    public void setDwgReadExecutable(String dwgReadExecutable) {
        if (!Paths.get(dwgReadExecutable).isAbsolute())
            try {
                dwgReadExecutable =   new File(dwgReadExecutable).getCanonicalFile().toString();
            } catch (IOException e) {
                //do nothing as the error will be picked up by the DWG Parser
            }


        this.dwgReadExecutable = dwgReadExecutable;
    }

    public void setCleanDwgReadOutput(boolean cleanDwgReadOutput) {
        this.cleanDwgReadOutput = cleanDwgReadOutput;
    }

    public void setCleanDwgReadOutputBatchSize(int cleanDwgReadOutputBatchSize) {
        this.cleanDwgReadOutputBatchSize = cleanDwgReadOutputBatchSize;
    }

    public void setDwgReadtimeout(long dwgReadtimeout) {
        this.dwgReadTimeout = dwgReadtimeout;
    }

    public void setCleanDwgReadRegexToReplace(String cleanDwgReadRegexToReplace) {
        this.cleanDwgReadRegexToReplace = cleanDwgReadRegexToReplace;
    }

    public void setCleanDwgReadReplaceWith(String cleanDwgReadReplaceWith) {
        this.cleanDwgReadReplaceWith = cleanDwgReadReplaceWith;
    }

}
