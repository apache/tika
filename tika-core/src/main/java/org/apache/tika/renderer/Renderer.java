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
package org.apache.tika.renderer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Interface for a renderer.  This should be flexible enough to run on the initial design: PDF pages
 * but also on portions of PDF pages as well as on other document types.
 *
 */
public interface Renderer extends Serializable {



    /**
     * Returns the set of media types supported by this renderer when used
     * with the given parse context.
     *
     * @param context parse context
     * @return immutable set of media types
     * @since Apache Tika 2.5.0
     */
    Set<MediaType> getSupportedTypes(ParseContext context);

    RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext,
                         RenderRequest ... requests) throws IOException,
            TikaException;

    /*
    At some point, we might need/want to add something like this, where for a given
    page the requestor or the parser determines that they only want to render e.g. a
    box within a page.

    RenderResults render(InputStream is, int page, Coordinates coordinates, Metadata metadata,
                         ParseContext parseContext) throws IOException,
            TikaException;

     */
}
