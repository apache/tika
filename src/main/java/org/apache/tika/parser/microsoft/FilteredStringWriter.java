/**
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

package org.apache.tika.parser.microsoft;

import java.io.StringWriter;

/**
 * Writes to optimize ASCII output. Not needed chars are filtered (ignored).
 * 
 * 
 */
public class FilteredStringWriter extends StringWriter {

  /**
   * @see StringWriter#StringWriter()
   */
  public FilteredStringWriter() {
    super();
  }

  /**
   * @param initialSize
   * @see StringWriter#StringWriter(int)
   */
  public FilteredStringWriter(final int initialSize) {
    super(initialSize);
  }

  /**
   * Chars which are not useful for Nutch indexing are filtered (ignored) on
   * writing to the writer.
   * 
   * @see java.io.Writer#write(int)
   */
  public void write(final int ch) {
    if (ch == '\r') {
      // PowerPoint seems to store files with \r as the line break
      // -> unify to platform specific format
      super.write(System.getProperty("line.separator"));
    } else if (ch == 0) {
      super.write(System.getProperty("line.separator"));
    } else if (ch == '\b') {
      // ignore it
    } else if (Character.isISOControl((char) ch)) {
      // replace by blank
      // super.write(' ');
    } else if (Character.isWhitespace((char) ch)) {
      // unify to blank
      super.write(' ');
    } else {
      super.write(ch);
    }
  }
}
