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
 * <p>
 * <b>Important - Accessing Results:</b>
 * <ul>
 *   <li><b>RMETA mode (default):</b> Use {@link #getMetadataList()} to access content and
 *       metadata from the container document AND all embedded documents. The convenience
 *       methods {@link #getContent()} and {@link #getMetadata()} only return the container
 *       document's data - embedded document content will be missed!</li>
 *   <li><b>CONCATENATE mode:</b> Include only metadata from the container document, but
 *       concatenated content from the container document and all attachments.</li>
 * </ul>
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
     * <b>This is the recommended method for RMETA mode (the default).</b>
     * <p>
     * <b>RMETA mode:</b> Returns one metadata object per document - the first is
     * the container document, followed by each embedded document. Each metadata
     * object contains:
     * <ul>
     *   <li>Content via {@link TikaCoreProperties#TIKA_CONTENT}</li>
     *   <li>Document metadata (title, author, dates, etc.)</li>
     *   <li>Any parse exceptions via {@link TikaCoreProperties#EMBEDDED_EXCEPTION}</li>
     * </ul>
     * <p>
     * <b>CONCATENATE mode:</b> Returns a single metadata object containing the
     * container's metadata and concatenated content from all documents.
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
     * Get the content from the container document only.
     * <p>
     * <b>WARNING - RMETA mode:</b> In RMETA mode, this returns ONLY the container
     * document's content. Content from embedded documents is NOT included. To get
     * all content including embedded documents, iterate over {@link #getMetadataList()}
     * and retrieve {@link TikaCoreProperties#TIKA_CONTENT} from each metadata object.
     * <p>
     * <b>CONCATENATE mode:</b> In CONCATENATE mode, this returns all content
     * (container + embedded) since everything is concatenated into a single
     * metadata object. This method works as expected in CONCATENATE mode.
     * <p>
     * <b>Recommendation:</b> For RMETA mode (the default), use {@link #getMetadataList()}
     * to access content from all documents. This method is most appropriate for
     * CONCATENATE mode or when you only need the container document's content.
     *
     * @return the container document's content, or null if not available
     * @see #getMetadataList()
     */
    public String getContent() {
        List<Metadata> metadataList = getMetadataList();
        if (metadataList.isEmpty()) {
            return null;
        }
        return metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
    }

    /**
     * Get the container document's metadata only.
     * <p>
     * <b>WARNING - RMETA mode:</b> In RMETA mode, this returns ONLY the container
     * document's metadata. Metadata from embedded documents (including their content,
     * titles, authors, and any parse exceptions) is NOT included. To access metadata
     * from all documents, use {@link #getMetadataList()}.
     * <p>
     * <b>CONCATENATE mode:</b> In CONCATENATE mode, there is only one metadata
     * object containing the container's metadata and concatenated content from
     * all documents. By the nature of CONCATENATE mode, you are losing metadata
     * from embedded files, and Tika is silently swallowing exceptions in embedded files.
     * <p>
     * <b>Recommendation:</b> For RMETA mode (the default), use {@link #getMetadataList()}
     * to access metadata from all documents, including embedded document exceptions
     * (stored in {@link TikaCoreProperties#EMBEDDED_EXCEPTION}).
     *
     * @return the container document's metadata, or null if not available
     * @see #getMetadataList()
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
