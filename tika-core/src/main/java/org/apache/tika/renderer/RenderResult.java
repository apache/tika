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

import java.nio.file.Path;

import org.apache.tika.metadata.Metadata;

public class RenderResult {

    public enum STATUS {
        SUCCESS,
        EXCEPTION,
        TIMEOUT
    }
    private final STATUS status;

    private final int id;
    private final Path path;
    //TODO: we're relying on metadata to bring in a bunch of info.
    //Might be cleaner to add specific parameters for page number, embedded path, etc.?
    private final Metadata metadata;

    public RenderResult(STATUS status, int id, Path path, Metadata metadata) {
        this.status = status;
        this.id = id;
        this.path = path;
        this.metadata = metadata;
    }

    public Path getPath() {
        return path;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public STATUS getStatus() {
        return status;
    }

    public int getId() {
        return id;
    }


}
