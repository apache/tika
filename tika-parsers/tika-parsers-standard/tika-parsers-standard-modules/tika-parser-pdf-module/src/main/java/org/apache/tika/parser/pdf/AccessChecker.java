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
package org.apache.tika.parser.pdf;

import java.io.Serializable;

import org.apache.tika.exception.AccessPermissionException;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;

/**
 * Checks whether or not a document allows extraction generally
 * or extraction for accessibility only.
 */
public class AccessChecker implements Serializable {

    private static final long serialVersionUID = 6492570218190936987L;

    /**
     * Mode for checking document access permissions.
     */
    public enum AccessCheckMode {
        /**
         * Don't check extraction permissions. Content will always be extracted
         * regardless of document permissions. This is the default for backwards
         * compatibility with Tika's legacy behavior (&lt;= v1.7).
         */
        DONT_CHECK,

        /**
         * Check permissions, but allow extraction for accessibility purposes.
         * If general extraction is blocked but accessibility extraction is allowed,
         * content will be extracted.
         */
        ALLOW_EXTRACTION_FOR_ACCESSIBILITY,

        /**
         * Enforce document permissions strictly. If extraction is blocked,
         * an {@link AccessPermissionException} will be thrown.
         */
        ENFORCE_PERMISSIONS
    }

    private AccessCheckMode mode;

    /**
     * Constructs an {@link AccessChecker} with {@link AccessCheckMode#DONT_CHECK}.
     * This will not perform any checking and will always return without
     * throwing an exception.
     * <p/>
     * This constructor is available to allow for Tika's legacy (&lt;= v1.7) behavior.
     */
    public AccessChecker() {
        this.mode = AccessCheckMode.DONT_CHECK;
    }

    /**
     * Constructs an {@link AccessChecker} with the specified mode.
     *
     * @param mode the access check mode
     */
    public AccessChecker(AccessCheckMode mode) {
        this.mode = mode;
    }

    public AccessCheckMode getMode() {
        return mode;
    }

    public void setMode(AccessCheckMode mode) {
        this.mode = mode;
    }

    /**
     * Checks to see if a document's content should be extracted based
     * on metadata values and the configured {@link AccessCheckMode}.
     *
     * @param metadata the document metadata containing access permissions
     * @throws AccessPermissionException if access is not permitted
     */
    public void check(Metadata metadata) throws AccessPermissionException {
        if (mode == AccessCheckMode.DONT_CHECK) {
            return;
        }

        if ("false".equals(metadata.get(AccessPermissions.EXTRACT_CONTENT))) {
            if (mode == AccessCheckMode.ALLOW_EXTRACTION_FOR_ACCESSIBILITY) {
                if ("true".equals(metadata.get(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY))) {
                    return;
                }
                throw new AccessPermissionException(
                        "Content extraction for accessibility is not allowed.");
            }
            throw new AccessPermissionException("Content extraction is not allowed.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AccessChecker checker = (AccessChecker) o;
        return mode == checker.mode;
    }

    @Override
    public int hashCode() {
        return mode != null ? mode.hashCode() : 0;
    }
}
