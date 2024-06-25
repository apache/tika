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
package org.apache.tika.serialization.mocks;

import java.util.Objects;

public class ClassA {
    private int a = 10;
    private float b = 11.1f;
    private short c = 2;
    private long d = 13l;
    private boolean e = false;
    private Integer f = 14;
    private Integer g = null;
    private Long h = 15l;
    private Long i = null;
    private Boolean j = Boolean.TRUE;
    private Boolean k = null;

    public int getA() {
        return a;
    }

    public void setA(int a) {
        this.a = a;
    }

    public float getB() {
        return b;
    }

    public void setB(float b) {
        this.b = b;
    }

    public short getC() {
        return c;
    }

    public void setC(short c) {
        this.c = c;
    }

    public long getD() {
        return d;
    }

    public void setD(long d) {
        this.d = d;
    }

    public boolean isE() {
        return e;
    }

    public void setE(boolean e) {
        this.e = e;
    }

    public Integer getF() {
        return f;
    }

    public void setF(Integer f) {
        this.f = f;
    }

    public Integer getG() {
        return g;
    }

    public void setG(Integer g) {
        this.g = g;
    }

    public Long getH() {
        return h;
    }

    public void setH(Long h) {
        this.h = h;
    }

    public Long getI() {
        return i;
    }

    public void setI(Long i) {
        this.i = i;
    }

    public Boolean getJ() {
        return j;
    }

    public void setJ(Boolean j) {
        this.j = j;
    }

    public Boolean getK() {
        return k;
    }

    public void setK(Boolean k) {
        this.k = k;
    }

    @Override
    public String toString() {
        return "ClassA{" + "a=" + a + ", b=" + b + ", c=" + c + ", d=" + d + ", e=" + e + ", f=" + f + ", g=" + g + ", h=" + h + ", i=" + i + ", j=" + j + ", k=" + k + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassA classA = (ClassA) o;
        return a == classA.a && Float.compare(b, classA.b) == 0 && c == classA.c && d == classA.d && e == classA.e && Objects.equals(f, classA.f) && Objects.equals(g, classA.g) &&
                Objects.equals(h, classA.h) && Objects.equals(i, classA.i) && Objects.equals(j, classA.j) && Objects.equals(k, classA.k);
    }

    @Override
    public int hashCode() {
        int result = a;
        result = 31 * result + Float.hashCode(b);
        result = 31 * result + c;
        result = 31 * result + Long.hashCode(d);
        result = 31 * result + Boolean.hashCode(e);
        result = 31 * result + Objects.hashCode(f);
        result = 31 * result + Objects.hashCode(g);
        result = 31 * result + Objects.hashCode(h);
        result = 31 * result + Objects.hashCode(i);
        result = 31 * result + Objects.hashCode(j);
        result = 31 * result + Objects.hashCode(k);
        return result;
    }
}
