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

    private static final long serialVersionUID = 6492570218190936986L;

    private final boolean needToCheck;
    private final boolean allowAccessibility;

    /**
     * This constructs an {@link AccessChecker} that
     * will not perform any checking and will always return without
     * throwing an exception.
     * <p>
     * This constructor is available to allow for Tika's legacy ( <= v1.7) behavior.
     */
    public AccessChecker() {
        needToCheck = false;
        allowAccessibility = true;
    }
    /**
     * This constructs an {@link AccessChecker} that will check
     * for whether or not content should be extracted from a document.
     *
     * @param allowExtractionForAccessibility if general extraction is not allowed, is extraction for accessibility allowed
     */
    public AccessChecker(boolean allowExtractionForAccessibility) {
        needToCheck = true;
        this.allowAccessibility = allowExtractionForAccessibility;
    }

    /**
     * Checks to see if a document's content should be extracted based
     * on metadata values and the value of {@link #allowAccessibility} in the constructor.
     *
     * @param metadata
     * @throws AccessPermissionException if access is not permitted
     */
    public void check(Metadata metadata) throws AccessPermissionException {
        if (!needToCheck) {
            return;
        }
        if ("false".equals(metadata.get(AccessPermissions.EXTRACT_CONTENT))) {
            if (allowAccessibility) {
                if("true".equals(metadata.get(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY))) {
                    return;
                }
                throw new AccessPermissionException("Content extraction for accessibility is not allowed.");
            }
            throw new AccessPermissionException("Content extraction is not allowed.");
        }
    }
}
