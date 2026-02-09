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
package org.apache.tika.config;

import java.io.Serializable;

import org.apache.tika.parser.ParseContext;

/**
 * Configuration for limits on embedded document processing.
 * <p>
 * This controls how deep and how many embedded documents are processed:
 * <ul>
 *   <li>{@code maxDepth} - maximum nesting depth for embedded documents (-1 = unlimited)</li>
 *   <li>{@code throwOnMaxDepth} - whether to throw an exception when maxDepth is reached</li>
 *   <li>{@code maxCount} - maximum number of embedded documents to process (-1 = unlimited)</li>
 *   <li>{@code throwOnMaxCount} - whether to throw an exception when maxCount is reached</li>
 * </ul>
 * <p>
 * <b>maxDepth behavior:</b> When the depth limit is reached, recursion stops but siblings at the
 * current level continue to be processed. For example, with maxDepth=1:
 * <pre>
 * container.zip (depth 0)
 * ├── doc1.docx (depth 1) ✓ PARSED
 * │   ├── image1.png (depth 2) ✗ NOT PARSED (exceeds maxDepth)
 * │   └── embed.xlsx (depth 2) ✗ NOT PARSED (exceeds maxDepth)
 * ├── doc2.pdf (depth 1) ✓ PARSED (sibling at same level)
 * └── doc3.txt (depth 1) ✓ PARSED (sibling at same level)
 * </pre>
 * <p>
 * <b>maxCount behavior:</b> When the count limit is reached, processing stops immediately.
 * No more embedded documents are processed, including siblings.
 * <p>
 * When a limit is hit and throwing is disabled:
 * <ul>
 *   <li>{@code X-TIKA-maxDepthReached=true} is set when maxDepth is hit</li>
 *   <li>{@code X-TIKA-maxEmbeddedCountReached=true} is set when maxCount is hit</li>
 * </ul>
 * <p>
 * Example configuration:
 * <pre>
 * {
 *   "parse-context": {
 *     "embedded-limits": {
 *       "maxDepth": 10,
 *       "throwOnMaxDepth": false,
 *       "maxCount": 1000,
 *       "throwOnMaxCount": false
 *     }
 *   }
 * }
 * </pre>
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "embedded-limits", spi = false)
public class EmbeddedLimits implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int UNLIMITED = -1;

    private int maxDepth = UNLIMITED;
    private boolean throwOnMaxDepth = false;
    private int maxCount = UNLIMITED;
    private boolean throwOnMaxCount = false;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public EmbeddedLimits() {
    }

    /**
     * Constructor with all parameters.
     *
     * @param maxDepth maximum nesting depth (-1 = unlimited)
     * @param throwOnMaxDepth whether to throw when depth limit is reached
     * @param maxCount maximum number of embedded documents (-1 = unlimited)
     * @param throwOnMaxCount whether to throw when count limit is reached
     */
    public EmbeddedLimits(int maxDepth, boolean throwOnMaxDepth, int maxCount, boolean throwOnMaxCount) {
        this.maxDepth = maxDepth;
        this.throwOnMaxDepth = throwOnMaxDepth;
        this.maxCount = maxCount;
        this.throwOnMaxCount = throwOnMaxCount;
    }

    /**
     * Gets the maximum nesting depth for embedded documents.
     *
     * @return maximum depth, or -1 for unlimited
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Sets the maximum nesting depth for embedded documents.
     *
     * @param maxDepth maximum depth, or -1 for unlimited
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Gets whether to throw an exception when maxDepth is reached.
     *
     * @return true if an exception should be thrown
     */
    public boolean isThrowOnMaxDepth() {
        return throwOnMaxDepth;
    }

    /**
     * Sets whether to throw an exception when maxDepth is reached.
     *
     * @param throwOnMaxDepth true to throw an exception
     */
    public void setThrowOnMaxDepth(boolean throwOnMaxDepth) {
        this.throwOnMaxDepth = throwOnMaxDepth;
    }

    /**
     * Gets the maximum number of embedded documents to process.
     *
     * @return maximum count, or -1 for unlimited
     */
    public int getMaxCount() {
        return maxCount;
    }

    /**
     * Sets the maximum number of embedded documents to process.
     *
     * @param maxCount maximum count, or -1 for unlimited
     */
    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    /**
     * Gets whether to throw an exception when maxCount is reached.
     *
     * @return true if an exception should be thrown
     */
    public boolean isThrowOnMaxCount() {
        return throwOnMaxCount;
    }

    /**
     * Sets whether to throw an exception when maxCount is reached.
     *
     * @param throwOnMaxCount true to throw an exception
     */
    public void setThrowOnMaxCount(boolean throwOnMaxCount) {
        this.throwOnMaxCount = throwOnMaxCount;
    }

    /**
     * Helper method to get EmbeddedLimits from ParseContext with defaults.
     *
     * @param context the ParseContext (may be null)
     * @return the EmbeddedLimits from context, or a new instance with defaults if not found
     */
    public static EmbeddedLimits get(ParseContext context) {
        if (context == null) {
            return new EmbeddedLimits();
        }
        EmbeddedLimits limits = context.get(EmbeddedLimits.class);
        return limits != null ? limits : new EmbeddedLimits();
    }

    @Override
    public String toString() {
        return "EmbeddedLimits{" +
                "maxDepth=" + maxDepth +
                ", throwOnMaxDepth=" + throwOnMaxDepth +
                ", maxCount=" + maxCount +
                ", throwOnMaxCount=" + throwOnMaxCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EmbeddedLimits that = (EmbeddedLimits) o;
        return maxDepth == that.maxDepth &&
                throwOnMaxDepth == that.throwOnMaxDepth &&
                maxCount == that.maxCount &&
                throwOnMaxCount == that.throwOnMaxCount;
    }

    @Override
    public int hashCode() {
        int result = maxDepth;
        result = 31 * result + (throwOnMaxDepth ? 1 : 0);
        result = 31 * result + maxCount;
        result = 31 * result + (throwOnMaxCount ? 1 : 0);
        return result;
    }
}
