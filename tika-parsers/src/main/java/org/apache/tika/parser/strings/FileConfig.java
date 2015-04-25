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

import java.io.Serializable;

/**
 * Configuration for the "file" (or file-alternative) command.
 *
 */
public class FileConfig implements Serializable {
	/**
	 * Serial version UID
	 */
	private static final long serialVersionUID = 5712655467296441314L;

	private String filePath = "";

	private boolean mimetype = false;

	/**
	 * Default constructor.
	 */
	public FileConfig() {
		// TODO Loads properties from InputStream.
	}

	/**
	 * Returns the "file" installation folder.
	 * 
	 * @return the "file" installation folder.
	 */
	public String getFilePath() {
		return filePath;
	}

	/**
	 * Sets the "file" installation folder.
	 * 
	 * @param path
	 *            the "file" installation folder.
	 */
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	/**
	 * Returns {@code true} if the mime option is enabled.
	 * 
	 * @return {@code true} if the mime option is enabled, {@code} otherwise.
	 */
	public boolean isMimetype() {
		return mimetype;
	}

	/**
	 * Sets the mime option. If {@code true}, it causes the file command to
	 * output mime type strings rather than the more traditional human readable
	 * ones.
	 * 
	 * @param mimetype
	 */
	public void setMimetype(boolean mimetype) {
		this.mimetype = mimetype;
	}
}
