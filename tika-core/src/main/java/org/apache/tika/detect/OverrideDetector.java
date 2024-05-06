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
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

/**
 * Use this to force a content type detection via the {@link
 * TikaCoreProperties#CONTENT_TYPE_USER_OVERRIDE} key in the metadata object.
 *
 * <p>This is also required to override detection by some parsers via {@link
 * TikaCoreProperties#CONTENT_TYPE_PARSER_OVERRIDE}.
 *
 * @deprecated after 2.5.0 this functionality was moved to the CompositeDetector
 */
@Deprecated
public class OverrideDetector implements Detector {

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        String type = metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE);
        if (type != null) {
            return MediaType.parse(type);
        }
        type = metadata.get(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE);
        if (type != null) {
            return MediaType.parse(type);
        } else {
            return MediaType.OCTET_STREAM;
        }
    }
}
