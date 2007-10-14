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
 * Package protected class for the required internal MS PowerPoint constants.
 * 
 * 
 */
class PPTConstants {

  /** ID of master slide */
  public static final long PPT_MASTERSLIDE = 1024L;

  /** ATOM ID of slide */
  public static long PPT_ATOM_SLIDE = 1007l;

  /** ATOM ID of notes */
  public static final long PPT_ATOM_NOTES = 1009L;

  /** ATOM ID of persistend slide */
  public static final long PPT_ATOM_SLIDEPERSISTANT = 1011L;

  /** ATOM ID of text char area. Holds text in byte swapped unicode form. */
  public static final long PPT_ATOM_TEXTCHAR = 4000L;

  /** ATOM ID of text byte area. Holds text in ascii form */
  public static final long PPT_ATOM_TEXTBYTE = 4008L;

  /** ATOM ID of user edit area */
  public static final long PPT_ATOM_USEREDIT = 4085L;

  /** ATOM ID of drawing group area */
  public static final long PPT_ATOM_DRAWINGGROUP = 61448L;

  /**
   * Protected constructor to prevent instantiation.
   */
  protected PPTConstants() {
    // nothing
  }
}