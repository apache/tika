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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Registration semantics of {@link Property}: first-wins interning, and the non-registering mint path. */
public class PropertyTest {

    @Test
    public void testFactoryRegistrationIsFirstWins() {
        String name = "prop-test:collide-" + System.nanoTime();
        Property first = Property.internalText(name);
        Property second = Property.internalInteger(name);

        // collision must not overwrite: the earlier-registered Property stays authoritative
        assertSame(first, Property.get(name));
        assertEquals(Property.ValueType.TEXT, Property.get(name).getValueType());
        assertEquals(Property.ValueType.INTEGER, second.getValueType(),
                "the losing Property object itself is still a valid, independent instance");
    }

    @Test
    public void testShapeMismatchedCollisionStillFirstWins() {
        // exercises the WARN branch (differing propertyType AND valueType); no log-capture
        // idiom exists in this module, so this only asserts the first-wins outcome
        String name = "prop-test:shape-mismatch-" + System.nanoTime();
        Property first = Property.internalText(name);
        Property second = Property.internalDateBag(name);

        assertSame(first, Property.get(name));
        assertEquals(Property.PropertyType.SIMPLE, Property.get(name).getPropertyType());
        assertEquals(Property.PropertyType.BAG, second.getPropertyType());
    }

    @Test
    public void testSameShapeReregistrationStaysQuietAndFirstWins() {
        // exercises the non-WARN branch: identical shape, so no mismatch to report
        String name = "prop-test:same-shape-" + System.nanoTime();
        Property first = Property.internalText(name);
        Property second = Property.internalText(name);

        assertSame(first, Property.get(name));
        assertFalse(first == second);
    }

    @Test
    public void testUnregisteredPropertyIsFunctionalButNotInRegistry() {
        String name = "prop-test:unregistered-" + System.nanoTime();
        assertNull(Property.get(name), "precondition: name must not already be registered");

        Property minted = Property.mintUnregistered(name, false, Property.PropertyType.SIMPLE,
                Property.ValueType.TEXT, null);

        assertEquals(name, minted.getName());
        assertEquals(Property.PropertyType.SIMPLE, minted.getPropertyType());
        assertEquals(Property.ValueType.TEXT, minted.getValueType());
        assertFalse(minted.isInternal());
        assertTrue(minted.isExternal());
        assertSame(minted, minted.getPrimaryProperty());

        // never interned: absent from both single lookup and prefix lookup
        assertNull(Property.get(name));
        assertFalse(Property.getProperties("prop-test").contains(minted));
    }

    @Test
    public void testUnregisteredPropertyDoesNotBlockOrGetBlockedByFactoryRegistration() {
        String name = "prop-test:coexist-" + System.nanoTime();

        Property minted = Property.mintUnregistered(name, true, Property.PropertyType.SIMPLE,
                Property.ValueType.TEXT, null);
        assertNull(Property.get(name));

        // a later factory call for the same name registers normally: the unregistered mint
        // left no trace in PROPERTIES to collide with
        Property registered = Property.internalText(name);
        assertSame(registered, Property.get(name));
        assertFalse(minted == registered);
    }

    @Test
    public void testUnregisteredPropertyWithChoicesAndBagType() {
        String name = "prop-test:bag-" + System.nanoTime();
        Property minted = Property.mintUnregistered(name, true, Property.PropertyType.BAG,
                Property.ValueType.CLOSED_CHOICE, new String[] {"a", "b"});

        assertEquals(Property.PropertyType.BAG, minted.getPropertyType());
        assertTrue(minted.isMultiValuePermitted());
        assertTrue(minted.getChoices().contains("a"));
        assertNull(Property.get(name));
    }
}
