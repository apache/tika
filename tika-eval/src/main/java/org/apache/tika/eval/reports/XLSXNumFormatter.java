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

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class XLSXNumFormatter implements XSLXCellFormatter {

    private final String formatString;
    private CellStyle style;

    XLSXNumFormatter(String formatString) {
        this.formatString = formatString;
    }


    @Override
    public void reset(XSSFWorkbook workbook) {
        style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat(formatString));
    }

    @Override
    public void applyStyleAndValue(int dbColNum, ResultSet resultSet, Cell cell) throws SQLException {
        double d = resultSet.getDouble(dbColNum);
        if (resultSet.wasNull()) {

        } else {
            cell.setCellStyle(style);
        }
        cell.setCellValue(resultSet.getDouble(dbColNum));
    }
}
