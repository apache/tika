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

package org.apache.tika.mime;

import org.apache.tika.mime.HexCoDec;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HexCoDecTest {

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Test
    public void decodeInput0PositivePositiveOutputIllegalArgumentException() {
        // Arrange
        final char[] hexChars = {};
        final int startIndex = 14;
        final int length = 1;

        // Act
        thrown.expect(IllegalArgumentException.class);
        HexCoDec.decode(hexChars, startIndex, length);

        // Method is not expected to return due to exception thrown
    }

    @Test
    public void decodeInput2Output1() {
        // Arrange
        final char[] hexChars = {'a', 'C'};

        // Act
        final byte[] retval = HexCoDec.decode(hexChars);

        // Assert result
        Assert.assertArrayEquals(new byte[] {(byte)-84}, retval);
    }

    @Test
    public void decodeInput2OutputIllegalArgumentException() {
        // Arrange
        final char[] hexChars = {'\u9080', '\u0000'};

        // Act
        thrown.expect(IllegalArgumentException.class);
        HexCoDec.decode(hexChars);

        // Method is not expected to return due to exception thrown
    }

    @Test
    public void decodeInput2OutputIllegalArgumentException2() {
        // Arrange
        final char[] hexChars = {'8', '\u0000'};

        // Act
        thrown.expect(IllegalArgumentException.class);
        HexCoDec.decode(hexChars);

        // Method is not expected to return due to exception thrown
    }

    @Test
    public void decodeInputNotNullOutput0() {
        // Arrange
        final String hexValue = "";

        // Act
        final byte[] retval = HexCoDec.decode(hexValue);

        // Assert result
        Assert.assertArrayEquals(new byte[] {}, retval);
    }

    @Test
    public void encodeInput1Output2() {
        // Arrange
        final byte[] bites = {(byte)102};

        // Act
        final char[] retval = HexCoDec.encode(bites);

        // Assert result
        Assert.assertArrayEquals(new char[] {'6', '6'}, retval);
    }

    @Test
    public void encodeInput1ZeroPositiveOutputArrayIndexOutOfBoundsException() {
        // Arrange
        final byte[] bites = {(byte)15};
        final int startIndex = 0;
        final int length = 4;

        // Act
        thrown.expect(ArrayIndexOutOfBoundsException.class);
        HexCoDec.encode(bites, startIndex, length);

        // Method is not expected to return due to exception thrown
    }
  }
