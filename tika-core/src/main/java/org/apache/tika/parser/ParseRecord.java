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
package org.apache.tika.parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.metadata.Metadata;

/**
 * Use this class to store exceptions, warnings and other information
 * during the parse.  This information is added to the parent's metadata
 * after the parse by the {@link CompositeParser}.
 */
public class ParseRecord {

    //hard limits so that specially crafted files
    //don't cause an OOM
    private static int MAX_PARSERS = 100;

    private static final int MAX_EXCEPTIONS = 100;

    private static final int MAX_WARNINGS = 100;

    private static final int MAX_METADATA_LIST_SIZE = 100;

    private int depth = 0;
    private final Set<String> parsers = new LinkedHashSet<>();

    private final List<Exception> exceptions = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    private final List<Metadata> metadataList = new ArrayList<>();

    private boolean writeLimitReached = false;

    void beforeParse() {
        depth++;
    }

    void afterParse() {
        depth--;
    }

    public int getDepth() {
        return depth;
    }

    public String[] getParsers() {
        return parsers.toArray(new String[0]);
    }

    void addParserClass(String parserClass) {
        if (parsers.size() < MAX_PARSERS) {
            parsers.add(parserClass);
        }
    }

    public void addException(Exception e) {
        if (exceptions.size() < MAX_EXCEPTIONS) {
            exceptions.add(e);
        }
    }

    public void addWarning(String msg) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(msg);
        }
    }

    public void addMetadata(Metadata metadata) {
        if (metadataList.size() < MAX_METADATA_LIST_SIZE) {
            metadataList.add(metadata);
        }
    }

    public void setWriteLimitReached(boolean writeLimitReached) {
        this.writeLimitReached = writeLimitReached;
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }

    public List<String> getWarnings() {
        return warnings;
    }


    public boolean isWriteLimitReached() {
        return writeLimitReached;
    }

    public List<Metadata> getMetadataList() {
        return metadataList;
    }
}
