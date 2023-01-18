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

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * If {@link Metadata} contains a {@link TikaCoreProperties#LATITUDE} and
 * a {@link TikaCoreProperties#LONGITUDE}, this filter concatenates those with a
 * comma in the order LATITUDE,LONGITUDE.
 *
 * If you need any other mappings, please open a ticket on our JIRA.
 */
public class GeoPointMetadataFilter extends MetadataFilter {

    String geoPointFieldName = "location";

    /**
     * Set the field for the concatenated LATITUDE,LONGITUDE string.
     * The default if &dquot;location&dquot;
     *
     * @param geoPointFieldName field name to use for the geopoint field
     */
    @Field
    public void setGeoPointFieldName(String geoPointFieldName) {
        this.geoPointFieldName = geoPointFieldName;
    }

    @Override
    public void filter(Metadata metadata) throws TikaException {
        String lat = metadata.get(TikaCoreProperties.LATITUDE);
        if (StringUtils.isEmpty(lat)) {
            return;
        }
        String lng = metadata.get(TikaCoreProperties.LONGITUDE);
        if (StringUtils.isEmpty(lng)) {
            return;
        }
        metadata.set(geoPointFieldName, lat + "," + lng);
    }
}
