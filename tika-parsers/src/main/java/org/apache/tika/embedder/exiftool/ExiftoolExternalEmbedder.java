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
package org.apache.tika.embedder.exiftool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.embedder.ExternalEmbedder;
import org.apache.tika.metadata.Property;
import org.apache.tika.parser.exiftool.ExiftoolExecutableUtils;
import org.apache.tika.parser.exiftool.ExiftoolTikaMapper;
import org.apache.tika.parser.external.ExternalParser;

/**
 * Convenience class to programmatically create an {@link ExternalEmbedder} which uses ExifTool.
 *
 * @see <a href="http://www.sno.phy.queensu.ca/~phil/exiftool/">ExifTool</a>
 */
public class ExiftoolExternalEmbedder extends ExternalEmbedder {

    private static final long serialVersionUID = 6037513204935762760L;

    private static final String COMMAND_APPEND_OPERATOR = "+=";

    private final String runtimeExiftoolExecutable;

    private final ExiftoolTikaMapper exiftoolTikaMapper;

    /**
     * Default constructor
     */
    public ExiftoolExternalEmbedder(ExiftoolTikaMapper exiftoolTikaMapper) {
        super();
        this.exiftoolTikaMapper = exiftoolTikaMapper;
        this.runtimeExiftoolExecutable = null;
        init();
    }

    public ExiftoolExternalEmbedder(ExiftoolTikaMapper exiftoolTikaMapper, String runtimeExiftoolExecutable) {
        super();
        this.exiftoolTikaMapper = exiftoolTikaMapper;
        this.runtimeExiftoolExecutable = runtimeExiftoolExecutable;
        init();
    }

    /**
     * Programmatically sets up the metadata to command line arguments map, sets the executable, and append operator.
     */
    public void init() {
        // Convert the exiftool metadata names into command line arguments
        Map<Property, String[]> metadataCommandArguments = new HashMap<Property, String[]>();
        for (Property tikaMetadata : exiftoolTikaMapper.getTikaToExiftoolMetadataMap().keySet()) {
            List<Property> exiftoolMetadataNames = exiftoolTikaMapper.getTikaToExiftoolMetadataMap().get(tikaMetadata);
            String[] exiftoolCommandArguments = new String[exiftoolMetadataNames.size()];
            for (int i = 0; i < exiftoolMetadataNames.size(); i++) {
                exiftoolCommandArguments[i] = "-" + exiftoolMetadataNames.get(i).getName();
            }
            metadataCommandArguments.put(tikaMetadata, exiftoolCommandArguments);
        }
        setMetadataCommandArguments(metadataCommandArguments);

        setExiftoolExecutable(
                ExiftoolExecutableUtils.getExiftoolExecutable(runtimeExiftoolExecutable));
        setCommandAppendOperator(COMMAND_APPEND_OPERATOR);
    }

    /**
     * Sets the path to the executable exiftool.
     *
     * @param exiftoolExecutable
     */
    public void setExiftoolExecutable(String exiftoolExecutable) {
        String[] cmd = new String[] { exiftoolExecutable, "-v0",
                "-o", ExternalParser.OUTPUT_FILE_TOKEN, "-m",
                ExternalEmbedder.METADATA_COMMAND_ARGUMENTS_TOKEN,
                ExternalParser.INPUT_FILE_TOKEN };
        setCommand(cmd);
    }

}
