package org.apache.tika.metadata.serialization;

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


import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class JsonMetadata extends JsonMetadataBase{
    private static Gson GSON;

    static {
        GSON = defaultInit();
    }
    /**
     * Serializes a Metadata object to Json.  This does not flush or close the writer.
     * 
     * @param metadata metadata to write
     * @param writer writer
     * @throws TikaException if there is an IOException during writing
     */
    public static void toJson(Metadata metadata, Writer writer) throws TikaException {
        try {
            GSON.toJson(metadata, writer);
        } catch (JsonIOException e) {
            throw new TikaException(e.getMessage());
        }
    }
        
    /**
     * Read metadata from reader.
     *
     * @param reader reader to read from
     * @return Metadata or null if nothing could be read from the reader
     * @throws TikaException in case of parse failure by Gson or IO failure with Reader
     */
    public static Metadata fromJson(Reader reader) throws TikaException {
        Metadata m = null;
        try {
            m = GSON.fromJson(reader, Metadata.class);
        } catch (com.google.gson.JsonParseException e){
            //covers both io and parse exceptions
            throw new TikaException(e.getMessage());
        }
        return m;
    }

    /**
     * Enables setting custom configurations on Gson.  Remember to register
     * a serializer and a deserializer for Metadata.  This does a literal set
     * and does not add the default serializer and deserializers.
     *
     * @param gson
     */
    public static void setGson(Gson gson) {
        GSON = gson;
    }

    public static void setPrettyPrinting(boolean prettyPrint) {
        if (prettyPrint) {
            GSON = prettyInit();
        } else {
            GSON = defaultInit();
        }
    }

}
