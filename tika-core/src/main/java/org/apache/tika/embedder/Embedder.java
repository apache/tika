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
package org.apache.tika.embedder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Tika embedder interface
 *
 * @since Apache Tika 1.3
 */
public interface Embedder extends Serializable {

    /**
     * Returns the set of media types supported by this embedder when used with
     * the given parse context.
     * <p>
     * The name differs from the precedence of {@link Parser#getSupportedTypes(ParseContext)}
     * so that parser implementations may also choose to implement this interface.
     *
     * @param context parse context
     * @return immutable set of media types
     */
    Set<MediaType> getSupportedEmbedTypes(ParseContext context);

    /**
     * Embeds related document metadata from the given metadata object into the
     * given output stream.
     * <p>
     * The given document stream is consumed but not closed by this method. The
     * responsibility to close the stream remains on the caller.
     * <p>
     * Information about the parsing context can be passed in the context
     * parameter. See the parser implementations for the kinds of context
     * information they expect.
     * <p>
     * In general implementations should favor preserving the source file's metadata 
     * unless an update to a field is explicitly defined in the Metadata object.
     * More specifically:
     * <ul>
     *  <li>Embedder implementations should only attempt to update metadata fields
     *  present in the given Metadata object.  Other fields should be left untouched.</li>
     *  <li>Embedder implementations should set properties as empty when the 
     *  corresponding field in the Metadata object is an empty string, i.e. ""</li>
     *  <li>Embedder implementations should nullify or delete properties 
     *  corresponding to fields with a null value in the given Metadata object.</li>
     *  <li>Embedder implementations should set the property 
     *  corresponding to a particular field in the given Metadata object in all 
     *  metadata containers whenever possible and appropriate for the file format at the time. 
     *  If a particular metadata container falls out of use and/or is superseded by another 
     *  (such as IIC vs XMP for IPTC) it is up to the implementation to decide if and when 
     *  to cease embedding in the alternate container.</li>
     *  <li>Embedder implementations should attempt to embed as much of the metadata 
     *  as accurately as possible. An implementation may choose a strict approach 
     *  and throw an exception if a value to be embedded exceeds the length allowed 
     *  or may choose to truncate the value.</li>
     * </ul>
     * 
     * @param metadata document metadata (input and output)
     * @param originalStream the document stream (input)
     * @param outputStream the output stream to write the metadata embedded data to
     * @param context parse context
     * @throws IOException if the document stream could not be read
     * @throws TikaException if the document could not be parsed
     */
    void embed(Metadata metadata, InputStream originalStream,
            OutputStream outputStream, ParseContext context)
            throws IOException, TikaException;

}
