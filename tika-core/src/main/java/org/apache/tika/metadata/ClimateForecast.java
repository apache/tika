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
 * href="http://cf-pcmdi.llnl.gov/">Climate Forecast Convention</a>. Key suffixes are the
 * external convention's own attribute names, verbatim (not Tika-coined) — some contain
 * underscores per that convention — under the {@code cf:} namespace: bare generic tokens
 * ({@code comment}, {@code source}, {@code history}) would collide indistinguishably in
 * the global keyspace (TIKA-4816 round-3 review).
 */
public interface ClimateForecast {

    String PREFIX = "cf" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    Property PROGRAM_ID = Property.externalText(PREFIX + "prg_ID");

    Property COMMAND_LINE = Property.externalText(PREFIX + "cmd_ln");

    // BAG: CF history is a provenance trail, appended once per processing step
    // (NetCDFParser/GribParser addGlobalAttribute).
    Property HISTORY = Property.externalTextBag(PREFIX + "history");

    Property TABLE_ID = Property.externalText(PREFIX + "table_id");

    Property INSTITUTION = Property.externalText(PREFIX + "institution");

    Property SOURCE = Property.externalText(PREFIX + "source");

    Property CONTACT = Property.externalText(PREFIX + "contact");

    Property PROJECT_ID = Property.externalText(PREFIX + "project_id");

    Property CONVENTIONS = Property.externalText(PREFIX + "Conventions");

    Property REFERENCES = Property.externalText(PREFIX + "references");

    Property ACKNOWLEDGEMENT = Property.externalText(PREFIX + "acknowledgement");

    Property REALIZATION = Property.externalText(PREFIX + "realization");

    Property EXPERIMENT_ID = Property.externalText(PREFIX + "experiment_id");

    Property COMMENT = Property.externalText(PREFIX + "comment");

    Property MODEL_NAME_ENGLISH = Property.externalText(PREFIX + "model_name_english");

}
