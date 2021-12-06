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
package org.apache.tika.parser.microsoft.fsshttpb.property;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to represent the property contains no data.
 */
public class NoData implements IProperty {
    /**
     * This method is used to deserialize the NoData from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return
     */
    public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        return 0;
    }

    /**
     * This method is used to convert the element of NoData into a byte List.
     *
     * @return Return the byte list which store the byte information of NoData.
     */
    public List<Byte> SerializeToByteList() {
        return new ArrayList<>();
    }
}