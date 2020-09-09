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
package org.apache.tika.parser.dbf;

import java.util.Arrays;

class DBFRow {

    DBFCell[] cells;
    private boolean isDeleted = false;

    DBFRow(DBFFileHeader header) {
        cells = new DBFCell[header.getCols().length];
        for (int i = 0; i < cells.length; i++) {
            DBFColumnHeader columnHeader = header.getCols()[i];
            cells[i] = new DBFCell(columnHeader.getColType(),
                    columnHeader.fieldLength,
                    columnHeader.decimalCount);
        }
    }

    private DBFRow() {}

    void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    boolean isDeleted() {
        return isDeleted;
    }

    DBFRow deepCopy() {
        DBFRow row = new DBFRow();
        row.isDeleted = this.isDeleted;
        row.cells = new DBFCell[cells.length];
        for (int i = 0; i < cells.length; i++) {
            row.cells[i] = cells[i].deepCopy();
        }
        return row;
    }

    @Override
    public String toString() {
        return "DBFRow{" +
                "cells=" + Arrays.toString(cells) +
                '}';
    }
}
