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
package org.apache.tika.pipes.emitter;

import java.io.Serializable;
import java.util.Objects;

public class EmitKey implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

    private String emitterName;
    private String emitKey;

    //for serialization only...yuck.
    public EmitKey() {

    }
    public EmitKey(String emitterName, String emitKey) {
        this.emitterName = emitterName;
        this.emitKey = emitKey;
    }

    public String getEmitterName() {
        return emitterName;
    }

    public String getEmitKey() {
        return emitKey;
    }

    @Override
    public String toString() {
        return "EmitterKey{" + "emitterName='" + emitterName + '\'' + ", emitterKey='" + emitKey +
                '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EmitKey emitKey1 = (EmitKey) o;

        if (!Objects.equals(emitterName, emitKey1.emitterName)) {
            return false;
        }
        return Objects.equals(emitKey, emitKey1.emitKey);
    }

    @Override
    public int hashCode() {
        int result = emitterName != null ? emitterName.hashCode() : 0;
        result = 31 * result + (emitKey != null ? emitKey.hashCode() : 0);
        return result;
    }
}
