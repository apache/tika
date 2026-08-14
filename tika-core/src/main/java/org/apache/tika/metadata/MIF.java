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
 * FrameMaker MIF properties collection.
 */
public interface MIF {

    String MIF_PREFIX = "mif" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Count of MIF {@code LeftMasterPage}/{@code RightMasterPage}/{@code OtherMasterPage} pages.
     */
    Property MASTER_PAGE_COUNT = Property.internalInteger(MIF_PREFIX + "master-page-count");

    /**
     * Count of MIF {@code ReferencePage} pages.
     */
    Property REFERENCE_PAGE_COUNT = Property.internalInteger(MIF_PREFIX + "reference-page-count");

    /**
     * Sum of body, master, and reference page counts.
     */
    Property TOTAL_PAGE_COUNT = Property.internalInteger(MIF_PREFIX + "total-page-count");
}
