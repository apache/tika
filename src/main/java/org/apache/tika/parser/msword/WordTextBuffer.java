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
package org.apache.tika.parser.msword;


/**
 * This class acts as a StringBuffer for text from a word document. It allows
 * processing of character before they
 * @author Ryan Ackley
 * @version 1.0
 */
public class WordTextBuffer
{
  StringBuffer _buf;
  boolean _hold;

  public WordTextBuffer()
  {
    _buf = new StringBuffer();
    _hold = false;
  }

  public void append(String text)
  {
    char[] letters = text.toCharArray();
    for (int x = 0; x < letters.length; x++)
    {
      switch(letters[x])
      {
        case '\r':
          _buf.append("\r\n");
          break;
        case 0x13:
          _hold = true;
          break;
        case 0x14:
          _hold = false;
          break;
        default:
          if (!_hold)
          {
            _buf.append(letters[x]);
          }
          break;
      }
    }
  }

  public String toString()
  {
    return _buf.toString();
  }

}
