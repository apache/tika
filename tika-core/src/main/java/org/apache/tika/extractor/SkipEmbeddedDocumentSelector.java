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
package org.apache.tika.extractor;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;

/**
 * A {@link DocumentSelector} that skips all embedded documents.
 * When this selector is set on the {@link org.apache.tika.parser.ParseContext},
 * no embedded documents will be extracted during parsing.
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(contextKey = DocumentSelector.class)
public class SkipEmbeddedDocumentSelector implements DocumentSelector {

    @Override
    public boolean select(Metadata metadata) {
        return false;
    }
}
