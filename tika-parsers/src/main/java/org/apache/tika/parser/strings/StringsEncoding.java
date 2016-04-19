/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

/**
 * Character encoding of the strings that are to be found using the "strings" command.
 *
 */
public enum StringsEncoding {
	SINGLE_7_BIT('s', "single-7-bit-byte"), // default
	SINGLE_8_BIT('S', "single-8-bit-byte"),
	BIGENDIAN_16_BIT('b', "16-bit bigendian"),
	LITTLEENDIAN_16_BIT('l', "16-bit littleendian"),
	BIGENDIAN_32_BIT('B', "32-bit bigendian"),
	LITTLEENDIAN_32_BIT('L', "32-bit littleendian");
	
	private char value;
	
	private String encoding;
	
	private StringsEncoding(char value, String encoding) {
		this.value = value;
		this.encoding = encoding;
	}
	
	public char get() {
		return value;
	}
	
	@Override
	public String toString() {
		return encoding;
	}
}
