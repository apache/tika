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
package org.apache.tika.parser.iwork;

import junit.framework.TestCase;

/**
 * Test class for the <code>AutoPageNumberUtils</code> helper class.
 */
public class AutoPageNumberUtilsTest extends TestCase {

	/**
	 * Check upper-case alpha-numeric numbers are generated based on the 
	 * input page number.
	 */
	public void testAlphaUpper() {
		assertEquals("A", AutoPageNumberUtils.asAlphaNumeric(1));
		assertEquals("Z", AutoPageNumberUtils.asAlphaNumeric(26));
		assertEquals("AA", AutoPageNumberUtils.asAlphaNumeric(27));
		assertEquals("ZZ", AutoPageNumberUtils.asAlphaNumeric(52));
		assertEquals("AAA", AutoPageNumberUtils.asAlphaNumeric(53));
		assertEquals("ZZZ", AutoPageNumberUtils.asAlphaNumeric(78));
	}

	/**
	 * Check lower-case alpha-numeric numbers are generated based on the 
	 * input page number.
	 */
	public void testAlphaLower() {
		assertEquals("a", AutoPageNumberUtils.asAlphaNumericLower(1));
		assertEquals("z", AutoPageNumberUtils.asAlphaNumericLower(26));
		assertEquals("aa", AutoPageNumberUtils.asAlphaNumericLower(27));
		assertEquals("zz", AutoPageNumberUtils.asAlphaNumericLower(52));
		assertEquals("aaa", AutoPageNumberUtils.asAlphaNumericLower(53));
		assertEquals("zzz", AutoPageNumberUtils.asAlphaNumericLower(78));
	}

	/**
	 * Check upper-case Roman numerals numbers are generated based on the 
	 * input page number.
	 */
	public void testRomanUpper() {
		assertEquals("I", AutoPageNumberUtils.asRomanNumerals(1));
		assertEquals("XXVI", AutoPageNumberUtils.asRomanNumerals(26));
		assertEquals("XXVII", AutoPageNumberUtils.asRomanNumerals(27));
	}

	/**
	 * Check lower-case Roman numerals numbers are generated based on the 
	 * input page number.
	 */
	public void testRomanLower() {
		assertEquals("i", AutoPageNumberUtils.asRomanNumeralsLower(1));
		assertEquals("xxvi", AutoPageNumberUtils.asRomanNumeralsLower(26));
		assertEquals("xxvii", AutoPageNumberUtils.asRomanNumeralsLower(27));
	}

}
