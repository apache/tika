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
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

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
	 * Default contructor.
	 */
	public StringsConfig() {
		init(this.getClass().getResourceAsStream("Strings.properties"));
	}

	/**
	 * Loads properties from InputStream and then tries to close InputStream. If
	 * there is an IOException, this silently swallows the exception and goes
	 * back to the default.
	 *
	 * @param is
	 */
	public StringsConfig(InputStream is) {
		init(is);
	}

	/**
	 * Initializes attributes.
	 *
	 * @param is
	 */
	private void init(InputStream is) {
		if (is == null) {
			return;
		}
		Properties props = new Properties();
		try {
			props.load(is);
		} catch (IOException e) {
			// swallow
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// swallow
				}
			}
		}

		setStringsPath(props.getProperty("stringsPath", "" + getStringsPath()));
		
		setMinLength(Integer.parseInt(props.getProperty("minLength", ""
				+ getMinLength())));

		setEncoding(StringsEncoding.valueOf(props.getProperty("encoding", ""
				+ getEncoding().get())));

		setTimeout(Integer.parseInt(props.getProperty("timeout", ""
				+ getTimeout())));
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
	 * @return {@see StringsEncoding} enum that represents the character
	 *         encoding of the strings that are to be found.
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
	 * @param path
	 *            the "strings" installation folder.
	 */
	public void setStringsPath(String path) {
		if (!path.isEmpty() && !path.endsWith(File.separator)) {
			path += File.separatorChar;
		}
		this.stringsPath = path;
	}

	/**
	 * Sets the minimum sequence length (characters) to print.
	 * 
	 * @param minLength
	 *            the minimum sequence length (characters) to print.
	 */
	public void setMinLength(int minLength) {
		if (minLength < 1) {
			throw new IllegalArgumentException("Invalid minimum length");
		}
		this.minLength = minLength;
	}

	/**
	 * Sets the character encoding of the strings that are to be found.
	 * 
	 * @param encoding
	 *            {@see StringsEncoding} enum that represents the character
	 *            encoding of the strings that are to be found.
	 */
	public void setEncoding(StringsEncoding encoding) {
		this.encoding = encoding;
	}

	/**
	 * Sets the maximum time (in seconds) to wait for the "strings" command to
	 * terminate.
	 * 
	 * @param timeout
	 *            the maximum time (in seconds) to wait for the "strings"
	 *            command to terminate.
	 */
	public void setTimeout(int timeout) {
		if (timeout < 1) {
			throw new IllegalArgumentException("Invalid timeout");
		}
		this.timeout = timeout;
	}
}
