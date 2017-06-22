package org.apache.tika.metadata; /*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public interface HTML {
    public static final String PREFIX_HTML_META = "html_meta";


    /**
     * If a script element contains a src value, this value
     * is set in the embedded document's metadata
     */
    Property SCRIPT_SOURCE = Property.internalText(PREFIX_HTML_META +
            Metadata.NAMESPACE_PREFIX_DELIMITER + "scriptSrc");

}
