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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.AccessPermissionException;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PropertyTypeException;

public class AccessCheckerTest {

    @Test
    public void testLegacy() throws AccessPermissionException {
        //test that there are no thrown exceptions
        Metadata m = getMetadata(false, false);
        //legacy behavior; don't bother checking
        AccessChecker checker = new AccessChecker();
        checker.check(m);

        m = getMetadata(false, true);
        checker.check(m);

        m = getMetadata(true, true);
        checker.check(m);
    }

    @Test
    public void testNoExtraction() {

        Metadata m = null;
        //allow nothing
        AccessChecker checker = new AccessChecker(false);
        boolean ex = false;
        try {
            m = getMetadata(false, false);
            checker.check(m);
        } catch (AccessPermissionException e) {
            ex = true;
        }
        assertTrue(ex, "correct exception with no extraction, no extract for accessibility");
        ex = false;
        try {
            //document allows extraction for accessibility
            m = getMetadata(false, true);
            checker.check(m);
        } catch (AccessPermissionException e) {
            //but application is not an accessibility application
            ex = true;
        }
        assertTrue(ex, "correct exception with no extraction, no extract for accessibility");
    }

    @Test
    public void testExtractOnlyForAccessibility() throws AccessPermissionException {
        Metadata m = getMetadata(false, true);
        //allow accessibility
        AccessChecker checker = new AccessChecker(true);
        checker.check(m);
        assertTrue(true, "no exception");
        boolean ex = false;
        try {
            m = getMetadata(false, false);
            checker.check(m);
        } catch (AccessPermissionException e) {
            ex = true;
        }
        assertTrue(ex, "correct exception");
    }

    @Test
    public void testIllogicalExtractNotForAccessibility() throws AccessPermissionException {
        Metadata m = getMetadata(true, false);
        //allow accessibility
        AccessChecker checker = new AccessChecker(true);
        checker.check(m);
        assertTrue(true, "no exception");

        //don't extract for accessibility
        checker = new AccessChecker(false);
        //if extract content is allowed, the checker shouldn't
        //check the value of extract for accessibility
        checker.check(m);
        assertTrue(true, "no exception");

    }

    @Test
    public void testCantAddMultiplesToMetadata() {
        Metadata m = new Metadata();
        boolean ex = false;
        m.add(AccessPermissions.EXTRACT_CONTENT, "true");
        try {
            m.add(AccessPermissions.EXTRACT_CONTENT, "false");
        } catch (PropertyTypeException e) {
            ex = true;
        }
        assertTrue(ex, "can't add multiple values");

        m = new Metadata();
        ex = false;
        m.add(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY, "true");
        try {
            m.add(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY, "false");
        } catch (PropertyTypeException e) {
            ex = true;
        }
        assertTrue(ex, "can't add multiple values");
    }

    private Metadata getMetadata(boolean allowExtraction, boolean allowExtractionForAccessibility) {
        Metadata m = new Metadata();
        m.set(AccessPermissions.EXTRACT_CONTENT, Boolean.toString(allowExtraction));
        m.set(AccessPermissions.EXTRACT_FOR_ACCESSIBILITY,
                Boolean.toString(allowExtractionForAccessibility));
        return m;
    }
}
