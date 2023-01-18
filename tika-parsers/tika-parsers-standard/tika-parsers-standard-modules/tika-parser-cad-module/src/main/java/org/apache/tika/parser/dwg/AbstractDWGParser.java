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


import org.apache.tika.config.Field;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;




public abstract class AbstractDWGParser extends AbstractParser {


    /**
     * 
     */
    private static final long serialVersionUID = 6261810259683381984L;
    private final DWGParserConfig defaultDwgParserConfig = new DWGParserConfig();

    public void configure(ParseContext parseContext) {
        DWGParserConfig dwgParserConfig =  parseContext.get(DWGParserConfig.class, defaultDwgParserConfig);
        parseContext.set(DWGParserConfig.class, dwgParserConfig);
    }


    public String getDwgReadExecutable() {
        return defaultDwgParserConfig.getDwgReadExecutable();
    }
    
    @Field
    public void setDwgReadExecutable(String dwgReadExecutable) {
        defaultDwgParserConfig.setDwgReadExecutable(dwgReadExecutable);
    }
    
    public boolean isCleanDwgReadOutput() {
        return defaultDwgParserConfig.isCleanDwgReadOutput();
    }
    
    @Field
    public void setCleanDwgReadOutput(boolean cleanDwgReadOutput) {
        defaultDwgParserConfig.setCleanDwgReadOutput(cleanDwgReadOutput);
    }

    public int getCleanDwgReadOutputBatchSize() {
        return defaultDwgParserConfig.getCleanDwgReadOutputBatchSize();
    }
    
    @Field
    public void setCleanDwgReadOutputBatchSize(int cleanDwgReadOutputBatchSize) {
        defaultDwgParserConfig.setCleanDwgReadOutputBatchSize(cleanDwgReadOutputBatchSize);
    }
    public String getCleanDwgReadRegexToReplace() {
        return defaultDwgParserConfig.getCleanDwgReadRegexToReplace();
    }
    
    @Field
    public void setCleanDwgReadRegexToReplace(String cleanDwgReadRegexToReplace) {
        defaultDwgParserConfig.setCleanDwgReadRegexToReplace(cleanDwgReadRegexToReplace);
    }
    public String getCleanDwgReadReplaceWith() {
        return defaultDwgParserConfig.getCleanDwgReadReplaceWith();
    }
    
    @Field
    public void setCleanDwgReadReplaceWith(String cleanDwgReadReplaceWith) {
        defaultDwgParserConfig.setCleanDwgReadReplaceWith(cleanDwgReadReplaceWith);
    }
    public long getDwgReadTimeout() {
        return defaultDwgParserConfig.getDwgReadTimeout();
    }

    @Field
    public void setDwgReadTimeout(long dwgReadTimeout) {
        defaultDwgParserConfig.setDwgReadtimeout(dwgReadTimeout);
    }
    
}
