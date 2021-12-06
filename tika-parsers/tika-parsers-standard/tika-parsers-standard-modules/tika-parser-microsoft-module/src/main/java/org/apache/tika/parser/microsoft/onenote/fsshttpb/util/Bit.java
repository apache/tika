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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.util;

/**
 * The class is used to read/set bit value for a byte array
 */
public class Bit {
    /**
     * Read a bit value from a byte array with the specified bit position.
     *
     * @param array Specify the byte array.
     * @param bit   Specify the bit position.
     * @return Return the bit value in the specified bit position.
     */
    public static boolean IsBitSet(byte[] array, long bit) {
        return (array[(int) (bit / 8)] & (1 << (int) (bit % 8))) != 0;
    }

    /**
     * Set a bit value to "On" in the specified byte array with the specified bit position.
     *
     * @param array Specify the byte array.
     * @param bit   Specify the bit position.
     */
    public static void SetBit(byte[] array, long bit) {
        array[(int) (bit / 8)] |= (byte) (1 << (int) (bit % 8));
    }

    /**
     * Set a bit value to "Off" in the specified byte array with the specified bit position.
     *
     * @param array Specify the byte array.
     * @param bit   Specify the bit position.
     */
    public static void ClearBit(byte[] array, long bit) {
        array[(int) (bit / 8)] &= (byte) (~(1 << (int) (bit % 8)));
    }
}