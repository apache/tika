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
package org.apache.tika.parser.external;

/**
 * Met Keys used by the {@link ExternalParsersConfigReader}.
 */
public interface ExternalParsersConfigReaderMetKeys {

    String EXTERNAL_PARSERS_TAG = "external-parsers";

    String PARSER_TAG = "parser";

    String COMMAND_TAG = "command";
    
    String CHECK_TAG = "check";
    
    String ERROR_CODES_TAG = "error-codes";
    
    String MIMETYPES_TAG = "mime-types";
    
    String MIMETYPE_TAG = "mime-type";
    
    String METADATA_TAG = "metadata";
    
    String METADATA_MATCH_TAG = "match";
    
    String METADATA_KEY_ATTR = "key";
}
