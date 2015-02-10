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

import java.io.File;
import java.io.Serializable;

/**
 * Configuration for the "strings" (or strings-alternative) command.
 *
 */
public class StringsConfig implements Serializable {
	/**
	 * Serial version UID
	 */
	private static final long serialVersionUID = -1465227101645003594L;

	private String stringsPath = "";
	
	// Minimum sequence length (characters) to print
	private int minLength = 4;
	
	// Character encoding of the strings that are to be found
	private StringsEncoding encoding = StringsEncoding.SINGLE_7_BIT;

	// Maximum time (seconds) to wait for the strings process termination
	private int timeout = 120;

	/**
	 * Default constructor.
	 */
	public StringsConfig() {
		// TODO Loads properties from InputStream.
	}

	/**
	 * Returns the "strings" installation folder.
	 * 
	 * @return the "strings" installation folder.
	 */
	public String getStringsPath() {
		return this.stringsPath;
	}
	
	/**
	 * Returns the minimum sequence length (characters) to print.
	 * 
	 * @return the minimum sequence length (characters) to print.
	 */
	public int getMinLength() {
		return this.minLength;
	}
	
	/**
	 * Returns the character encoding of the strings that are to be found.
	 * 
	 * @return {@see StringsEncoding} enum that represents the character encoding of the strings that are to be found.
	 */
	public StringsEncoding getEncoding() {
		return this.encoding;
	}

	/**
	 * Returns the maximum time (in seconds) to wait for the "strings" command
	 * to terminate.
	 * 
	 * @return the maximum time (in seconds) to wait for the "strings" command
	 *         to terminate.
	 */
	public int getTimeout() {
		return this.timeout;
	}

	/**
	 * Sets the "strings" installation folder.
	 * 
	 * @param path the "strings" installation folder.
	 */
	public void setStringsPath(String path) {
		char lastChar = path.charAt(path.length() - 1);

		if (lastChar != File.separatorChar) {
			path += File.separatorChar;
		}
		this.stringsPath = path;
	}
	
	/**
	 * Sets the minimum sequence length (characters) to print.
	 * 
	 * @param minLength the minimum sequence length (characters) to print.
	 */
	public void setMinLength(int minLength) {
		this.minLength = minLength;
	}
	
	/**
	 * Sets the character encoding of the strings that are to be found.
	 * 
	 * @param encoding {@see StringsEncoding} enum that represents the character encoding of the strings that are to be found.
	 */
	public void setEncodings(StringsEncoding encoding) {
		this.encoding = encoding;
	}

	/**
	 * Sets the maximum time (in seconds) to wait for the "strings" command to terminate.
	 * @param timeout the maximum time (in seconds) to wait for the "strings" command to terminate.
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
}
