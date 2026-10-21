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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.parser.ParseContext;

/**
 * Configuration for output and security limits.
 * <p>
 * This controls output size and various security thresholds:
 * <ul>
 *   <li>{@code writeLimit} - maximum characters to write (-1 = unlimited)</li>
 *   <li>{@code throwOnWriteLimit} - whether to throw an exception when writeLimit is reached</li>
 *   <li>{@code maxXmlDepth} - maximum XML element nesting depth (default: 100)</li>
 *   <li>{@code maxPackageEntryDepth} - deprecated; use {@link EmbeddedLimits#setMaxDepth(int)}</li>
 *   <li>{@code zipBombThreshold} - characters before zip bomb check activates (default: 1,000,000)</li>
 *   <li>{@code zipBombRatio} - maximum output:input ratio for zip bomb detection (default: 1,000)</li>
 * </ul>
 * <p>
 * <b>writeLimit behavior:</b> The writeLimit is the TOTAL characters across all documents
 * (container + embedded). When the limit is reached:
 * <ul>
 *   <li>If {@code throwOnWriteLimit=false}: Output is truncated, {@code tk:exception:write-limit-reached=true} is set</li>
 *   <li>If {@code throwOnWriteLimit=true}: {@code WriteLimitReachedException} is thrown</li>
 * </ul>
 * <p>
 * <b>Security limits:</b> maxXmlDepth and zipBomb
 * limits always throw exceptions when exceeded (no silent truncation option).
 * <p>
 * Example configuration:
 * <pre>
 * {
 *   "parse-context": {
 *     "output-limits": {
 *       "writeLimit": 100000,
 *       "throwOnWriteLimit": false,
 *       "maxXmlDepth": 100,
 * *       "zipBombThreshold": 1000000,
 *       "zipBombRatio": 1000
 *     }
 *   }
 * }
 * </pre>
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(spi = false)
public class OutputLimits implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(OutputLimits.class);

    public static final int UNLIMITED = -1;

    // Output limits
    private int writeLimit = UNLIMITED;
    private boolean throwOnWriteLimit = false;

    // XML/Security limits
    private int maxXmlDepth = 100;
    private int maxPackageEntryDepth = 20;
    private long zipBombThreshold = 1_000_000;
    private long zipBombRatio = 1000;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public OutputLimits() {
    }

    /**
     * Constructor with all parameters.
     *
     * @param writeLimit maximum characters to write (-1 = unlimited)
     * @param throwOnWriteLimit whether to throw when write limit is reached
     * @param maxXmlDepth maximum XML element nesting depth
     * @param zipBombThreshold characters before zip bomb check activates
     * @param zipBombRatio maximum output:input ratio
     */
    public OutputLimits(int writeLimit, boolean throwOnWriteLimit, int maxXmlDepth,
                        long zipBombThreshold, long zipBombRatio) {
        this.writeLimit = writeLimit;
        this.throwOnWriteLimit = throwOnWriteLimit;
        this.maxXmlDepth = maxXmlDepth;
        this.zipBombThreshold = zipBombThreshold;
        this.zipBombRatio = zipBombRatio;
    }

    /**
     * @deprecated package-entry depth only bounds nesting when embedded content is
     * inlined into its container's XHTML; use {@link EmbeddedLimits#setMaxDepth(int)},
     * which bounds nesting on every path. To be removed in 4.3.
     */
    @Deprecated(since = "4.2", forRemoval = true)
    public OutputLimits(int writeLimit, boolean throwOnWriteLimit,
                        int maxXmlDepth, int maxPackageEntryDepth,
                        long zipBombThreshold, long zipBombRatio) {
        this(writeLimit, throwOnWriteLimit, maxXmlDepth, zipBombThreshold, zipBombRatio);
        setMaxPackageEntryDepth(maxPackageEntryDepth);
    }

    /**
     * Gets the maximum characters to write.
     *
     * @return maximum characters, or -1 for unlimited
     */
    public int getWriteLimit() {
        return writeLimit;
    }

    /**
     * Sets the maximum characters to write.
     *
     * @param writeLimit maximum characters, or -1 for unlimited
     */
    public void setWriteLimit(int writeLimit) {
        this.writeLimit = writeLimit;
    }

    /**
     * Gets whether to throw an exception when writeLimit is reached.
     *
     * @return true if an exception should be thrown
     */
    public boolean isThrowOnWriteLimit() {
        return throwOnWriteLimit;
    }

    /**
     * Sets whether to throw an exception when writeLimit is reached.
     *
     * @param throwOnWriteLimit true to throw an exception
     */
    public void setThrowOnWriteLimit(boolean throwOnWriteLimit) {
        this.throwOnWriteLimit = throwOnWriteLimit;
    }

    /**
     * Gets the maximum XML element nesting depth.
     *
     * @return maximum XML depth
     */
    public int getMaxXmlDepth() {
        return maxXmlDepth;
    }

    /**
     * Sets the maximum XML element nesting depth.
     *
     * @param maxXmlDepth maximum XML depth
     */
    public void setMaxXmlDepth(int maxXmlDepth) {
        this.maxXmlDepth = maxXmlDepth;
    }

    /**
     * @deprecated use {@link EmbeddedLimits#getMaxDepth()}. To be removed in 4.3.
     */
    @Deprecated(since = "4.2", forRemoval = true)
    public int getMaxPackageEntryDepth() {
        return maxPackageEntryDepth;
    }

    /**
     * @deprecated package-entry depth only bounds nesting when embedded content is
     * inlined into its container's XHTML; use {@link EmbeddedLimits#setMaxDepth(int)},
     * which bounds nesting on every path. To be removed in 4.3.
     */
    @Deprecated(since = "4.2", forRemoval = true)
    public void setMaxPackageEntryDepth(int maxPackageEntryDepth) {
        LOG.warn("maxPackageEntryDepth is deprecated and will be removed in 4.3; " +
                "use embedded-limits maxDepth");
        this.maxPackageEntryDepth = maxPackageEntryDepth;
    }

    /**
     * Gets the zip bomb threshold (characters before check activates).
     *
     * @return zip bomb threshold
     */
    public long getZipBombThreshold() {
        return zipBombThreshold;
    }

    /**
     * Sets the zip bomb threshold (characters before check activates).
     *
     * @param zipBombThreshold zip bomb threshold
     */
    public void setZipBombThreshold(long zipBombThreshold) {
        this.zipBombThreshold = zipBombThreshold;
    }

    /**
     * Gets the zip bomb ratio (maximum output:input ratio).
     *
     * @return zip bomb ratio
     */
    public long getZipBombRatio() {
        return zipBombRatio;
    }

    /**
     * Sets the zip bomb ratio (maximum output:input ratio).
     *
     * @param zipBombRatio zip bomb ratio
     */
    public void setZipBombRatio(long zipBombRatio) {
        this.zipBombRatio = zipBombRatio;
    }

    /**
     * Returns this instance if no limit is looser than {@code ceiling}'s, otherwise a copy
     * with each looser limit reduced to the ceiling's. A negative write limit is unlimited.
     * Used at trust boundaries so a request may tighten operator limits but not loosen them.
     *
     * @param ceiling the operator's limits
     * @return this, or a tightened copy
     * @since Apache Tika 4.2
     */
    public OutputLimits tightenedTo(OutputLimits ceiling) {
        int wl = EmbeddedLimits.tighter(writeLimit, ceiling.writeLimit);
        int xmlDepth = Math.min(maxXmlDepth, ceiling.maxXmlDepth);
        int pkgDepth = Math.min(maxPackageEntryDepth, ceiling.maxPackageEntryDepth);
        long threshold = Math.min(zipBombThreshold, ceiling.zipBombThreshold);
        long ratio = Math.min(zipBombRatio, ceiling.zipBombRatio);
        if (wl == writeLimit && xmlDepth == maxXmlDepth && pkgDepth == maxPackageEntryDepth
                && threshold == zipBombThreshold && ratio == zipBombRatio) {
            return this;
        }
        OutputLimits tightened = new OutputLimits(wl, throwOnWriteLimit, xmlDepth, threshold, ratio);
        tightened.maxPackageEntryDepth = pkgDepth;
        return tightened;
    }

    /**
     * Helper method to get OutputLimits from ParseContext with defaults.
     *
     * @param context the ParseContext (may be null)
     * @return the OutputLimits from context, or a new instance with defaults if not found
     */
    public static OutputLimits get(ParseContext context) {
        if (context == null) {
            return new OutputLimits();
        }
        OutputLimits limits = context.get(OutputLimits.class);
        return limits != null ? limits : new OutputLimits();
    }

    @Override
    public String toString() {
        return "OutputLimits{" +
                "writeLimit=" + writeLimit +
                ", throwOnWriteLimit=" + throwOnWriteLimit +
                ", maxXmlDepth=" + maxXmlDepth +
                ", maxPackageEntryDepth=" + maxPackageEntryDepth +
                ", zipBombThreshold=" + zipBombThreshold +
                ", zipBombRatio=" + zipBombRatio +
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
        OutputLimits that = (OutputLimits) o;
        return writeLimit == that.writeLimit &&
                throwOnWriteLimit == that.throwOnWriteLimit &&
                maxXmlDepth == that.maxXmlDepth &&
                maxPackageEntryDepth == that.maxPackageEntryDepth &&
                zipBombThreshold == that.zipBombThreshold &&
                zipBombRatio == that.zipBombRatio;
    }

    @Override
    public int hashCode() {
        int result = writeLimit;
        result = 31 * result + (throwOnWriteLimit ? 1 : 0);
        result = 31 * result + maxXmlDepth;
        result = 31 * result + maxPackageEntryDepth;
        result = 31 * result + (int) (zipBombThreshold ^ (zipBombThreshold >>> 32));
        result = 31 * result + (int) (zipBombRatio ^ (zipBombRatio >>> 32));
        return result;
    }
}
