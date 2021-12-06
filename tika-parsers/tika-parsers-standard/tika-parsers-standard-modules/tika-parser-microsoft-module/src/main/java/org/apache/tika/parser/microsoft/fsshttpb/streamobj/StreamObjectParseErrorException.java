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
package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

public class StreamObjectParseErrorException extends RuntimeException {


    /**
     * Gets or sets index of object.
     */
    public int Index;

    /**
     * Gets or sets stream object type name.
     */
    public String StreamObjectTypeName;

    /**
     * Initializes a new instance of the StreamObjectParseErrorException class
     *
     * @param index                Specify the index of object
     * @param streamObjectTypeName Specify the stream type name
     * @param innerException       Specify the inner exception
     */
    public StreamObjectParseErrorException(int index, String streamObjectTypeName, Exception innerException) {
        super(innerException);
        this.Index = index;
        this.StreamObjectTypeName = streamObjectTypeName;
    }

    /**
     * Initializes a new instance of the StreamObjectParseErrorException class
     *
     * @param index                Specify the index of object
     * @param streamObjectTypeName Specify the stream type name
     * @param message              Specify the exception message
     * @param innerException       Specify the inner exception
     */
    public StreamObjectParseErrorException(int index, String streamObjectTypeName, String message,
                                           Exception innerException) {
        super(message, innerException);
        this.Index = index;
        this.StreamObjectTypeName = streamObjectTypeName;
    }
}