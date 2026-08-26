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
package org.apache.tika.metadata;

/**
 * Adobe InDesign IDML properties collection.
 */
public interface IDML {

    String IDML_PREFIX = "idml" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Total page count summed across all body Spreads.
     */
    Property SPREAD_PAGE_COUNT = Property.internalInteger(IDML_PREFIX + "spread-page-count");

    /**
     * Total page count summed across all MasterSpreads.
     */
    Property MASTER_SPREAD_PAGE_COUNT =
            Property.internalInteger(IDML_PREFIX + "master-spread-page-count");
}
