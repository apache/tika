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
 *
 * Copyright (c) Data Geekery GmbH (http://www.datageekery.com)
 */

package org.apache.tika.parser.microsoft.onenote.fsshttpb.unsigned;

import java.math.BigInteger;

/**
 * The <code>unsigned short</code> type
 *
 * @author Lukas Eder
 * @author Jens Nerche
 */
public final class UShort extends UNumber implements Comparable<UShort> {

    /**
     * A constant holding the minimum value an <code>unsigned short</code> can
     * have, 0.
     */
    public static final int MIN_VALUE = 0x0000;
    /**
     * A constant holding the maximum value an <code>unsigned short</code> can
     * have, 2<sup>16</sup>-1.
     */
    public static final int MAX_VALUE = 0xffff;
    /**
     * A constant holding the minimum value an <code>unsigned short</code> can
     * have as UShort, 0.
     */
    public static final UShort MIN = valueOf(MIN_VALUE);
    /**
     * A constant holding the maximum value an <code>unsigned short</code> can
     * have as UShort, 2<sup>16</sup>-1.
     */
    public static final UShort MAX = valueOf(MAX_VALUE);
    /**
     * Generated UID
     */
    private static final long serialVersionUID = -6821055240959745390L;
    /**
     * The value modelling the content of this <code>unsigned short</code>
     */
    private final int value;

    /**
     * Create an <code>unsigned short</code>
     *
     * @throws NumberFormatException If <code>value</code> is not in the range
     *                               of an <code>unsigned short</code>
     */
    private UShort(int value) throws NumberFormatException {
        this.value = value;
        rangeCheck();
    }

    /**
     * Create an <code>unsigned short</code> by masking it with
     * <code>0xFFFF</code> i.e. <code>(short) -1</code> becomes
     * <code>(ushort) 65535</code>
     */
    private UShort(short value) {
        this.value = value & MAX_VALUE;
    }

    /**
     * Create an <code>unsigned short</code>
     *
     * @throws NumberFormatException If <code>value</code> does not contain a
     *                               parsable <code>unsigned short</code>.
     */
    private UShort(String value) throws NumberFormatException {
        this.value = Integer.parseInt(value);
        rangeCheck();
    }

    /**
     * Create an <code>unsigned short</code>
     *
     * @throws NumberFormatException If <code>value</code> does not contain a
     *                               parsable <code>unsigned short</code>.
     */
    public static UShort valueOf(String value) throws NumberFormatException {
        return new UShort(value);
    }

    /**
     * Create an <code>unsigned short</code> by masking it with
     * <code>0xFFFF</code> i.e. <code>(short) -1</code> becomes
     * <code>(ushort) 65535</code>
     */
    public static UShort valueOf(short value) {
        return new UShort(value);
    }

    /**
     * Create an <code>unsigned short</code>
     *
     * @throws NumberFormatException If <code>value</code> is not in the range
     *                               of an <code>unsigned short</code>
     */
    public static UShort valueOf(int value) throws NumberFormatException {
        return new UShort(value);
    }

    private void rangeCheck() throws NumberFormatException {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new NumberFormatException("Value is out of range : " + value);
        }
    }

    @Override
    public int intValue() {
        return value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public BigInteger toBigInteger() {
        return BigInteger.valueOf(value);
    }

    @Override
    public int hashCode() {
        return Integer.valueOf(value).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UShort) {
            return value == ((UShort) obj).value;
        }

        return false;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    @Override
    public int compareTo(UShort o) {
        return (value < o.value ? -1 : (value == o.value ? 0 : 1));
    }

    public UShort add(UShort val) throws NumberFormatException {
        return valueOf(value + val.value);
    }

    public UShort add(int val) throws NumberFormatException {
        return valueOf(value + val);
    }

    public UShort subtract(final UShort val) {
        return valueOf(value - val.value);
    }

    public UShort subtract(final int val) {
        return valueOf(value - val);
    }
}
