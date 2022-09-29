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
package org.apache.tika.pipes.reporters.opensearch;


import com.fasterxml.jackson.databind.JsonNode;

public class JsonResponse {

    private final int status;
    private final String msg;
    private final JsonNode root;

    public JsonResponse(int status, JsonNode root) {
        this.status = status;
        this.root = root;
        this.msg = null;
    }

    public JsonResponse(int status, String msg) {
        this.status = status;
        this.msg = msg;
        this.root = null;
    }

    public int getStatus() {
        return status;
    }

    public String getMsg() {
        return msg;
    }

    public JsonNode getJson() {
        return root;
    }

    @Override
    public String toString() {
        return "JsonResponse{" +
                "status=" + status +
                ", msg='" + msg + '\'' +
                ", root=" + root +
                '}';
    }
}
