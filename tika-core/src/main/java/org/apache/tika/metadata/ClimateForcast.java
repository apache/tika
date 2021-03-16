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

    String PROGRAM_ID = "prg_ID";

    String COMMAND_LINE = "cmd_ln";

    String HISTORY = "history";

    String TABLE_ID = "table_id";

    String INSTITUTION = "institution";

    String SOURCE = "source";

    String CONTACT = "contact";

    String PROJECT_ID = "project_id";

    String CONVENTIONS = "Conventions";

    String REFERENCES = "references";

    String ACKNOWLEDGEMENT = "acknowledgement";

    String REALIZATION = "realization";

    String EXPERIMENT_ID = "experiment_id";

    String COMMENT = "comment";

    String MODEL_NAME_ENGLISH = "model_name_english";

}
