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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.tika.parser.dbf.DBFColumnHeader.ColType.AT;
import static org.apache.tika.parser.dbf.DBFColumnHeader.ColType.NULL;
import static org.apache.tika.parser.dbf.DBFColumnHeader.ColType.PLUS;

class DBFColumnHeader {

    //from: http://www.dbf2002.com/dbf-file-format.html
    enum ColType {
        C,//character
        Y,//currency
        D,//date
        T,//datetime
        B,//double
        I,//integer,
        G,//general
        P,//picture
        F,//floating point binary numeric
        L,//logical
        M,//memo
        N,//binary coded decimal numeric
        PLUS, //autoincrement
        AT, //timestamp dbase level 7
        O, //double
        NULL //null
    }
    private final static Map<Integer, ColType> COL_TYPE_MAP =
            new ConcurrentHashMap<>();

    static {
        for (ColType type : ColType.values()) {
            if (type.equals(PLUS)) {
                COL_TYPE_MAP.put((int) '+', PLUS);
            } else if (type.equals(AT)) {
                COL_TYPE_MAP.put((int) '@', AT);
            } else if (type.equals(NULL)) {
                COL_TYPE_MAP.put((int)'0', NULL);
            } else {
                COL_TYPE_MAP.put((int) type.toString().charAt(0), type);
            }
        }
    }

    byte[] name;
    private ColType colType = null;
    int fieldLength = -1;
    int decimalCount = -1;

    public void setType(int type) {
        colType = COL_TYPE_MAP.get(type);
        if (colType == null) {
            throw new IllegalArgumentException("Unrecognized column type for column: " +
                    getName(StandardCharsets.US_ASCII) +
                    ". I regret I don't recognize: " + (char) type);
        }
    }

    ColType getColType() {
        return colType;
    }

    String getName(Charset charset) {
        return new String(name, charset).trim();
    }

    @Override
    public String toString() {
        return "DBFColumnHeader{" +
                "name='" + name + '\'' +
                ", colType=" + colType +
                ", fieldLength=" + fieldLength +
                ", decimalCount=" + decimalCount +
                '}';
    }
}
