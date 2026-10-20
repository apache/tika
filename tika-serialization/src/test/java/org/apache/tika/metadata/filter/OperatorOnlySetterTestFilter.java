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
package org.apache.tika.metadata.filter;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.OperatorOnly;
import org.apache.tika.metadata.Metadata;

/** A request-allowed filter with one operator-only property. */
@TikaComponent(name = "operator-only-setter-test-filter", spi = false)
public class OperatorOnlySetterTestFilter extends MetadataFilterBase {

    private String endpoint = "";
    private String label = "";

    public String getEndpoint() {
        return endpoint;
    }

    @OperatorOnly
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    protected void filter(Metadata metadata) {
    }
}
