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
package org.apache.tika.inference;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.utils.StringUtils;

/**
 * Extends {@link OpenAIEmbeddingFilter} for
 * <a href="https://jina.ai/embeddings/">Jina AI v5 text embeddings</a>.
 * <p>
 * The only difference from the standard OpenAI format is an optional
 * {@code "task"} field in the request body that instructs the Jina model
 * how to optimise the embedding.  Supported values include
 * {@code retrieval.passage} (default, for indexing documents),
 * {@code retrieval.query} (for query-time embeddings),
 * {@code text-matching}, {@code classification}, and {@code separation}.
 * <p>
 * Configuration key: {@code "jina-embedding-filter"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "jina-embedding-filter", spi = false)
public class JinaEmbeddingFilter extends OpenAIEmbeddingFilter {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Jina task type.  Default: {@code retrieval.passage} (for indexing).
     * Set to {@code retrieval.query} when embedding search queries.
     */
    private String task = "retrieval.passage";

    public JinaEmbeddingFilter() {
        super();
    }

    public JinaEmbeddingFilter(InferenceConfig config) {
        super(config);
    }

    @Override
    String buildRequest(List<Chunk> chunks, InferenceConfig config) {
        ObjectNode root = MAPPER.createObjectNode();
        if (!StringUtils.isBlank(config.getModel())) {
            root.put("model", config.getModel());
        }
        if (!StringUtils.isBlank(task)) {
            root.put("task", task);
        }
        ArrayNode input = root.putArray("input");
        for (Chunk chunk : chunks) {
            input.add(chunk.getText());
        }
        return root.toString();
    }

    public String getTask() {
        return task;
    }

    /**
     * Set the Jina task type.  Default is {@code retrieval.passage}.
     * Use {@code retrieval.query} when embedding search queries.
     */
    public void setTask(String task) {
        this.task = task;
    }
}
