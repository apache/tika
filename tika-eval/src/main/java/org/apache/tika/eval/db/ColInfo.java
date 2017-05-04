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
package org.apache.tika.eval.db;


import java.sql.Types;

public class ColInfo {
    private final Cols name;
    private final int type;
    private final Integer precision;
    private final String constraints;

    public ColInfo(Cols name, int type) {
        this(name, type, null, null);
    }

    public ColInfo(Cols name, int type, String constraints) {
        this(name, type, null, constraints);
    }

    public ColInfo(Cols name, int type, Integer precision) {
        this(name, type, precision, null);
    }


    public ColInfo(Cols name, int type, Integer precision, String constraints) {
        this.name = name;
        this.type = type;
        this.precision = precision;
        this.constraints = constraints;
    }

    public int getType() {
        return type;
    }

    public Cols getName() {
        return name;
    }
    /**
     *
     * @return constraints string or null
     */
    public String getConstraints() {
        return constraints;
    }

    /**
     * Gets the precision.  This can be null!
     * @return precision or null
     */
    public Integer getPrecision() {
        return precision;
    }

    public String getSqlDef() {
        if (type == Types.VARCHAR){
            return "VARCHAR("+precision+")";
        } else if (type == Types.CHAR) {
            return "CHAR("+precision+")";
        }
        switch (type) {
            case Types.FLOAT :
                return "FLOAT";
            case Types.DOUBLE :
                return "DOUBLE";
            case Types.BLOB :
                return "BLOB";
            case Types.INTEGER :
                return "INTEGER";
            case Types.BIGINT :
                return "BIGINT";
            case Types.BOOLEAN :
                return "BOOLEAN";
        }
        throw new UnsupportedOperationException("Don't yet recognize a type for: "+type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ColInfo colInfo = (ColInfo) o;

        if (type != colInfo.type) return false;
        if (name != colInfo.name) return false;
        if (precision != null ? !precision.equals(colInfo.precision) : colInfo.precision != null) return false;
        return !(constraints != null ? !constraints.equals(colInfo.constraints) : colInfo.constraints != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + type;
        result = 31 * result + (precision != null ? precision.hashCode() : 0);
        result = 31 * result + (constraints != null ? constraints.hashCode() : 0);
        return result;
    }
}
