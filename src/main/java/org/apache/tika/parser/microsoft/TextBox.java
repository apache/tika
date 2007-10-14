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

/**
 * Package protected class for the MS Powerpoint TextBox content
 * 
 * 
 */
class TextBox {

  /**
   * Current id of a text box
   */
  protected transient final long currentID;

  /**
   * Content of text box
   */
  protected String content;

  /**
   * Instantiates the text box object
   * 
   * @param textBoxId
   *          id of text box
   */
  public TextBox(final long textBoxId) {
    this.currentID = textBoxId;
    this.content = "";
  }

  /**
   * Instantiates the text box object
   * 
   * @param textBoxId
   *          id of text box
   * @param content
   *          content of text box
   */
  public TextBox(final long textBoxId, final String content) {
    this.currentID = textBoxId;
    this.content = content;
  }

  /**
   * Sets the content of the text box
   * 
   * @param content
   *          content of text Box
   */
  public void setContent(final String content) {
    this.content = content;
  }

  /**
   * Returns the content of the text box
   * 
   * @return content of text box
   */
  public String getContent() {
    return this.content;
  }

  /**
   * Returns the current text box id
   * 
   * @return long
   */
  public long getCurrentId() {
    return this.currentID;
  }
}