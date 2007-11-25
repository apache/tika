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



import org.apache.poi.util.LittleEndian;
import org.apache.poi.hwpf.model.*;

import java.io.IOException;
import java.util.*;

/**
 * This class is used to extract text from Word 6 documents only. It should
 * only be called from the org.textmining.text.extraction.WordExtractor because
 * it will automatically determine the version.
 *
 *
 */
class Word6Extractor
{

    private final Appendable appendable;

  public Word6Extractor(Appendable appendable)
  {
      this.appendable = appendable;
  }

  /**
   * Extracts the text
   *
   * @param mainStream The POIFS document stream entitled "WordDocument".
   *
   * @return The text from the document
   * @throws Exception If there are any unexpected exceptions.
   */
  public void extractText(byte[] mainStream) throws IOException {
    int fcMin = LittleEndian.getInt(mainStream, 0x18);
    int fcMax = LittleEndian.getInt(mainStream, 0x1C);

    int chpTableOffset = LittleEndian.getInt(mainStream, 0xb8);
    int chpTableSize = LittleEndian.getInt(mainStream, 0xbc);

    // get a list of character properties
    Word6CHPBinTable chpTable = new Word6CHPBinTable(mainStream, chpTableOffset,
      chpTableSize, fcMin);
    List textRuns = chpTable.getTextRuns();

    // iterate through the
    WordTextBuffer finalTextBuf = new WordTextBuffer(appendable);
    Iterator runsIt = textRuns.iterator();
    while(runsIt.hasNext())
    {
      CHPX chpx = (CHPX)runsIt.next();
      int runStart = chpx.getStart() + fcMin;
      int runEnd = chpx.getEnd() + fcMin;

      if (!isDeleted(chpx.getGrpprl()))
      {
        String s = new String(mainStream, runStart, Math.min(runEnd, fcMax) - runStart, "Cp1252");
        finalTextBuf.append(s);
        if (runEnd >= fcMax)
        {
          break;
        }
      }
    }
  }

  /**
   * Used to determine if a run of text has been deleted.
   * @param grpprl The list of sprms for this run of text.
   * @return
   */
  private boolean isDeleted(byte[] grpprl)
  {
    int offset = 0;
    boolean deleted = false;
    while (offset < grpprl.length)
    {
      switch (LittleEndian.getUnsignedByte(grpprl, offset++))
      {
        case 65:
          deleted = grpprl[offset++] != 0;
          break;
        case 66:
          offset++;
          break;
        case 67:
          offset++;
          break;
        case 68:
          offset += grpprl[offset];
          break;
        case 69:
          offset += 2;
          break;
        case 70:
          offset += 4;
          break;
        case 71:
          offset++;
          break;
        case 72:
          offset += 2;
          break;
        case 73:
          offset += 3;
          break;
        case 74:
          offset += grpprl[offset];
          break;
        case 75:
          offset++;
          break;
        case 80:
          offset += 2;
          break;
        case 81:
          offset += grpprl[offset];
          break;
        case 82:
          offset += grpprl[offset];
          break;
        case 83:
          break;
        case 85:
          offset++;
          break;
        case 86:
          offset++;
          break;
        case 87:
          offset++;
          break;
        case 88:
          offset++;
          break;
        case 89:
          offset++;
          break;
        case 90:
          offset++;
          break;
        case 91:
          offset++;
          break;
        case 92:
          offset++;
          break;
        case 93:
          offset += 2;
          break;
        case 94:
          offset++;
          break;
        case 95:
          offset += 3;
          break;
        case 96:
          offset += 2;
          break;
        case 97:
          offset += 2;
          break;
        case 98:
          offset++;
          break;
        case 99:
          offset++;
          break;
        case 100:
          offset++;
          break;
        case 101:
          offset++;
          break;
        case 102:
          offset++;
          break;
        case 103:
          offset += grpprl[offset];
          break;
        case 104:
          offset++;
          break;
        case 105:
          offset += grpprl[offset];
          break;
        case 106:
          offset += grpprl[offset];
          break;
        case 107:
          offset += 2;
          break;
        case 108:
          offset += grpprl[offset];
          break;
        case 109:
          offset += 2;
          break;
        case 110:
          offset += 2;
          break;
        case 117:
          offset++;
          break;
        case 118:
          offset++;
          break;

      }
    }
    return deleted;
  }
}
