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
package org.apache.tika.parser.ctakes;

import org.apache.uima.cas.impl.XCASSerializer;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.util.XmlCasSerializer;

/**
 * Enumeration for types of cTAKES (UIMA) CAS serializer supported by cTAKES.
 * <p>
 * A CAS serializer writes a CAS in the given format.
 */
public enum CTAKESSerializer {
    XCAS(XCASSerializer.class.getName()), XMI(XmiCasSerializer.class.getName()),
    XML(XmlCasSerializer.class.getName());

    private final String className;

    private CTAKESSerializer(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
}
