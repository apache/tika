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
package org.apache.tika.parser.climate;

import static org.apache.tika.metadata.ClimateForecast.ACKNOWLEDGEMENT;
import static org.apache.tika.metadata.ClimateForecast.COMMAND_LINE;
import static org.apache.tika.metadata.ClimateForecast.COMMENT;
import static org.apache.tika.metadata.ClimateForecast.CONTACT;
import static org.apache.tika.metadata.ClimateForecast.CONVENTIONS;
import static org.apache.tika.metadata.ClimateForecast.EXPERIMENT_ID;
import static org.apache.tika.metadata.ClimateForecast.HISTORY;
import static org.apache.tika.metadata.ClimateForecast.INSTITUTION;
import static org.apache.tika.metadata.ClimateForecast.MODEL_NAME_ENGLISH;
import static org.apache.tika.metadata.ClimateForecast.PROGRAM_ID;
import static org.apache.tika.metadata.ClimateForecast.PROJECT_ID;
import static org.apache.tika.metadata.ClimateForecast.REALIZATION;
import static org.apache.tika.metadata.ClimateForecast.REFERENCES;
import static org.apache.tika.metadata.ClimateForecast.SOURCE;
import static org.apache.tika.metadata.ClimateForecast.TABLE_ID;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tika.metadata.Property;

/**
 * CF-convention global-attribute name lookup shared by GribParser and NetCDFParser,
 * so a new convention constant only needs to be added once.
 */
public final class ClimateForecast {

    private static final Map<String, Property> BY_NAME = Stream
            .of(PROGRAM_ID, COMMAND_LINE, HISTORY, TABLE_ID, INSTITUTION, SOURCE, CONTACT,
                    PROJECT_ID, CONVENTIONS, REFERENCES, ACKNOWLEDGEMENT, REALIZATION,
                    EXPERIMENT_ID, COMMENT, MODEL_NAME_ENGLISH)
            .collect(Collectors.toMap(Property::getName, p -> p));

    private ClimateForecast() {
    }

    public static Property byName(String name) {
        return BY_NAME.get(name);
    }
}
