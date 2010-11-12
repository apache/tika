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
 * Met keys from NCAR CCSM files in the <a
 * href="http://cf-pcmdi.llnl.gov/">Climate Forecast Convention</a>.
 */
public interface ClimateForcast {

    public static final String PROGRAM_ID = "prg_ID";

    public static final String COMMAND_LINE = "cmd_ln";

    public static final String HISTORY = "history";

    public static final String TABLE_ID = "table_id";

    public static final String INSTITUTION = "institution";

    public static final String SOURCE = "source";

    public static final String CONTACT = "contact";

    public static final String PROJECT_ID = "project_id";

    public static final String CONVENTIONS = "Conventions";

    public static final String REFERENCES = "references";

    public static final String ACKNOWLEDGEMENT = "acknowledgement";

    public static final String REALIZATION = "realization";

    public static final String EXPERIMENT_ID = "experiment_id";

    public static final String COMMENT = "comment";

    public static final String MODEL_NAME_ENGLISH = "model_name_english";

}
