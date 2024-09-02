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
package org.apache.tika.pipes.emitter;

import java.io.IOException;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public interface Emitter {

    String getName();

    void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException, TikaEmitterException;

    void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException;
    //TODO -- add this later for xhtml?
    //void emit(String txt, Metadata metadata) throws IOException, TikaException;

}
