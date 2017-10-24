/*
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
package org.apache.tika.eval.reports;

import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.event.HyperlinkListener;


public class XLSXHREFFormatter implements XSLXCellFormatter {
    //xlsx files can only have this many hyperlinks
    //if they have more Excel can't read the file
    private static final int MAX_HYPERLINKS = 65000;


    private final String urlBase;
    private final HyperlinkType linkType;
    private XSSFWorkbook workbook;
    private CellStyle style;
    private int links = 0;

    public XLSXHREFFormatter(String urlBase,
                             HyperlinkType hyperlinkType) {
        this.urlBase = urlBase;
        this.linkType = hyperlinkType;
    }

    @Override
    public void reset(XSSFWorkbook workbook) {
        this.workbook = workbook;
        style = workbook.createCellStyle();
        Font hlinkFont = workbook.createFont();
        hlinkFont.setUnderline(Font.U_SINGLE);
        hlinkFont.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(hlinkFont);
        links = 0;

    }

    @Override
    public void applyStyleAndValue(int dbColNum, ResultSet resultSet, Cell cell) throws SQLException {
        if (links < MAX_HYPERLINKS) {
            Hyperlink hyperlink = workbook.getCreationHelper().createHyperlink(linkType);
            String path = resultSet.getString(dbColNum);
            String address = urlBase+path;
            hyperlink.setAddress(address);
            cell.setHyperlink(hyperlink);
            cell.setCellStyle(style);
            String fName = Paths.get(path).getFileName().toString();
            cell.setCellValue(fName);
            links++;
        } else {
            //silently stop adding hyperlinks
        }
    }
}
