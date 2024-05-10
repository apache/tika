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
package org.apache.tika.pipes.fetcher;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

/**
 * This class extracts a range of bytes from a given fetch key.
 */
public interface RangeFetcher extends Fetcher {
    //At some point, Tika 3.x?, we may want to add optional ranges to the fetchKey?

    default InputStream fetch(String fetchKey, long startOffset, long endOffset, Metadata fetchResponseMetadata)
            throws TikaException, IOException {
        return fetch(fetchKey, startOffset, endOffset, new Metadata(), fetchResponseMetadata);
    }

    InputStream fetch(String fetchKey, long startOffset, long endOffset, Metadata fetchRequestMetadata, Metadata fetchResponseMetadata)
            throws TikaException, IOException;
}
