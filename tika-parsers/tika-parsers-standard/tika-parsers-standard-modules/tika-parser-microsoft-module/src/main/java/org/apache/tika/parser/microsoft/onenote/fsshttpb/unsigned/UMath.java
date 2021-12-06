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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.unsigned;

public final class UMath {

    private UMath() {
    }

    /**
     * Returns the greater of two {@code UByte} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UByte max(UByte a, UByte b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code UInteger} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UInteger max(UInteger a, UInteger b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code ULong} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static ULong max(ULong a, ULong b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the greater of two {@code UShort} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UShort max(UShort a, UShort b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code UByte} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static UByte min(UByte a, UByte b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code UInteger} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static UInteger min(UInteger a, UInteger b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code ULong} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static ULong min(ULong a, ULong b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code UShort} values.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static UShort min(UShort a, UShort b) {
        return a.compareTo(b) < 0 ? a : b;
    }

}
