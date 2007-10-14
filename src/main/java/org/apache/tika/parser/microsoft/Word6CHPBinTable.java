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

import java.util.List;
import java.util.ArrayList;
import java.io.OutputStream;
import java.io.IOException;

import org.apache.poi.poifs.common.POIFSConstants;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.hwpf.model.io.*;
import org.apache.poi.hwpf.model.*;

/**
 * This class holds all of the character formatting properties from a Word
 * 6.0/95 document.
 *
 *
 */
public class Word6CHPBinTable
{
  /** List of character properties.*/
  ArrayList _textRuns = new ArrayList();

  /**
   * Constructor used to read a binTable in from a Word document.
   *
   * @param documentStream The POIFS "WordDocument" stream from a Word document
   * @param offset The offset of the Chp bin table in the main stream.
   * @param size The size of the Chp bin table in the main stream.
   * @param fcMin The start of text in the main stream.
   */
  public Word6CHPBinTable(byte[] documentStream, int offset,
                     int size, int fcMin)
  {
    PlexOfCps binTable = new PlexOfCps(documentStream, offset, size, 2);

    int length = binTable.length();
    for (int x = 0; x < length; x++)
    {
      GenericPropertyNode node = binTable.getProperty(x);

      int pageNum = LittleEndian.getShort((byte[])node.getBytes());
      int pageOffset = POIFSConstants.BIG_BLOCK_SIZE * pageNum;

      CHPFormattedDiskPage cfkp = new CHPFormattedDiskPage(documentStream,
        pageOffset, fcMin);

      int fkpSize = cfkp.size();

      for (int y = 0; y < fkpSize; y++)
      {
        _textRuns.add(cfkp.getCHPX(y));
      }
    }
  }

  public List getTextRuns()
  {
    return _textRuns;
  }

}
