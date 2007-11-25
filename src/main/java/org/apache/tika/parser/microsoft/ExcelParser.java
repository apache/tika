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

import java.io.IOException;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Excel parser
 */
public class ExcelParser extends OfficeParser {

    protected String getContentType() {
        return "application/vnd.ms-excel";
    }

    protected void extractText(POIFSFileSystem filesystem, Appendable builder) throws IOException{
        extractText(new HSSFWorkbook(filesystem), builder);
    }

    private void extractText(HSSFWorkbook book, Appendable builder) throws IOException {
        for (int i = 0; book != null && i < book.getNumberOfSheets(); i++) {
            extractText(book.getSheetAt(i), builder);
        }
    }

    private void extractText(HSSFSheet sheet, Appendable builder) throws IOException {
        for (int i = 0; sheet != null && i <= sheet.getLastRowNum(); i++) {
            extractText(sheet.getRow(i), builder);
        }
    }

    private void extractText(HSSFRow row, Appendable builder) throws IOException {
        for (short i = 0; row != null && i < row.getLastCellNum(); i++) {
            extractText(row.getCell(i), builder);
        }
    }

    private void extractText(HSSFCell cell, Appendable builder) throws IOException {
        if (cell != null) {
            switch (cell.getCellType()) {
            case HSSFCell.CELL_TYPE_STRING:
                addText(cell.getRichStringCellValue().getString(), builder);
                break;
            case HSSFCell.CELL_TYPE_NUMERIC:
            case HSSFCell.CELL_TYPE_FORMULA:
                addText(Double.toString(cell.getNumericCellValue()), builder);
                break;
            default:
                // ignore
            } 
        }
    }

    private void addText(String text, Appendable builder) throws IOException {
        if (text != null) {
            text = text.trim();
            if (text.length() > 0) {
                builder.append(text).append(' ');
            }
        }
    }

}
