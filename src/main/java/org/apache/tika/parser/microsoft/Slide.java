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

import java.util.List;
import java.util.Vector;

/**
 * Package protected class for a MS Powerpoint slide.
 * 
 * 
 */
class Slide {


  /** Holds the Slide Number */
  protected transient final long slideNumber;

  /** Holds the contents of the Slide */
  protected transient final List/* <String> */contents;

  /**
   * Initialise the Object for holding the contents of Power Point Slide
   * 
   * @param number
   */
  public Slide(long number) {
    this.slideNumber = number;
    this.contents = new Vector/* <String> */();
  }

  /**
   * Add the Content of Slide to this Object
   * 
   * @param content
   */
  public void addContent(String content) {
    this.contents.add(content);
  }

  /**
   * returns the contents of slide as a vector object
   * 
   * @return Vector
   */
  public List getContent() {
    return this.contents;
  }

  /**
   * returns the slide value
   * 
   * @return long
   */
  public long getSlideNumber() {
    return this.slideNumber;
  }
}