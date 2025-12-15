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
package org.apache.tika.pipes.fork;

import java.util.Collections;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.PipesResult;

/**
 * Result from parsing a file with {@link PipesForkParser}.
 * <p>
 * This wraps the {@link PipesResult} and provides convenient access to
 * the parsed content and metadata.
 * <p>
 * Content is available in the metadata via {@link TikaCoreProperties#TIKA_CONTENT}.
 */
public class PipesForkResult {

    private final PipesResult pipesResult;

    public PipesForkResult(PipesResult pipesResult) {
        this.pipesResult = pipesResult;
    }

    /**
     * Get the result status.
     *
     * @return the result status
     */
    public PipesResult.RESULT_STATUS getStatus() {
        return pipesResult.status();
    }

    /**
     * Check if the parsing was successful.
     *
     * @return true if parsing succeeded
     */
    public boolean isSuccess() {
        return pipesResult.isSuccess();
    }

    /**
     * Check if there was a process crash (OOM, timeout, etc.).
     *
     * @return true if the forked process crashed
     */
    public boolean isProcessCrash() {
        return pipesResult.isProcessCrash();
    }

    /**
     * Check if there was an application error.
     *
     * @return true if there was an application-level error
     */
    public boolean isApplicationError() {
        return pipesResult.isApplicationError();
    }

    /**
     * Get the list of metadata objects from parsing.
     * <p>
     * In RMETA mode, there will be one metadata object per document
     * (container plus embedded documents).
     * <p>
     * In CONCATENATE mode, there will be a single metadata object.
     * <p>
     * Content is available via {@link TikaCoreProperties#TIKA_CONTENT}.
     *
     * @return the list of metadata objects, or empty list if none
     */
    public List<Metadata> getMetadataList() {
        if (pipesResult.emitData() == null) {
            return Collections.emptyList();
        }
        return pipesResult.emitData().getMetadataList();
    }

    /**
     * Get the content from the first (or only) metadata object.
     * <p>
     * This is a convenience method for simple use cases.
     *
     * @return the content, or null if not available
     */
    public String getContent() {
        List<Metadata> metadataList = getMetadataList();
        if (metadataList.isEmpty()) {
            return null;
        }
        return metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
    }

    /**
     * Get the first (or only) metadata object.
     *
     * @return the metadata, or null if not available
     */
    public Metadata getMetadata() {
        List<Metadata> metadataList = getMetadataList();
        if (metadataList.isEmpty()) {
            return null;
        }
        return metadataList.get(0);
    }

    /**
     * Get any error message associated with the result.
     *
     * @return the error message, or null if none
     */
    public String getMessage() {
        return pipesResult.message();
    }

    /**
     * Get the underlying PipesResult for advanced access.
     *
     * @return the pipes result
     */
    public PipesResult getPipesResult() {
        return pipesResult;
    }

    @Override
    public String toString() {
        return "PipesForkResult{" +
                "status=" + getStatus() +
                ", metadataCount=" + getMetadataList().size() +
                ", message=" + getMessage() +
                '}';
    }
}
