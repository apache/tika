/*  Copyright 2004 Ryan Ackley
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tika.parser.microsoft;

/**
 * This class stores info about the data structure describing a chunk of text
 * in a Word document. Specifically, whether or not a Range of text uses
 * unicode or Cp1252 encoding.
 *
 *
 */

class WordTextPiece
{
  private int _fcStart;
  private boolean _usesUnicode;
  private int _length;

  public WordTextPiece(int start, int length, boolean unicode)
  {
    _usesUnicode = unicode;
    _length = length;
    _fcStart = start;
  }
   public boolean usesUnicode()
  {
      return _usesUnicode;
  }

  public int getStart()
  {
      return _fcStart;
  }
  public int getLength()
  {
    return _length;
  }



}
