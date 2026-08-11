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
 * href="http://cf-pcmdi.llnl.gov/">Climate Forecast Convention</a>. Key spellings are the
 * external convention's own attribute names, verbatim (not Tika-coined) — some contain
 * underscores per that convention.
 */
public interface ClimateForcast {

    Property PROGRAM_ID = Property.externalText("prg_ID");

    Property COMMAND_LINE = Property.externalText("cmd_ln");

    Property HISTORY = Property.externalText("history");

    Property TABLE_ID = Property.externalText("table_id");

    Property INSTITUTION = Property.externalText("institution");

    Property SOURCE = Property.externalText("source");

    Property CONTACT = Property.externalText("contact");

    Property PROJECT_ID = Property.externalText("project_id");

    Property CONVENTIONS = Property.externalText("Conventions");

    Property REFERENCES = Property.externalText("references");

    Property ACKNOWLEDGEMENT = Property.externalText("acknowledgement");

    Property REALIZATION = Property.externalText("realization");

    Property EXPERIMENT_ID = Property.externalText("experiment_id");

    Property COMMENT = Property.externalText("comment");

    Property MODEL_NAME_ENGLISH = Property.externalText("model_name_english");

}
