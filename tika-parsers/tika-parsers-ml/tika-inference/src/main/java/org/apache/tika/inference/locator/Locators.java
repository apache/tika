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
package org.apache.tika.inference.locator;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for all locator types that identify where a chunk comes from
 * in the original content. Each locator type is optional; only populated
 * types are serialized.
 * <p>
 * A single chunk can have multiple locators of the same type (e.g.
 * a chunk that spans two pages has two {@link PaginatedLocator} entries).
 */
public class Locators {

    private List<TextLocator> text;
    private List<PaginatedLocator> paginated;
    private List<SpatialLocator> spatial;
    private List<TemporalLocator> temporal;

    public Locators() {
    }

    // ---- text -------------------------------------------------------------

    public List<TextLocator> getText() {
        return text;
    }

    public void setText(List<TextLocator> text) {
        this.text = text;
    }

    public Locators addText(TextLocator locator) {
        if (this.text == null) {
            this.text = new ArrayList<>();
        }
        this.text.add(locator);
        return this;
    }

    // ---- paginated --------------------------------------------------------

    public List<PaginatedLocator> getPaginated() {
        return paginated;
    }

    public void setPaginated(List<PaginatedLocator> paginated) {
        this.paginated = paginated;
    }

    public Locators addPaginated(PaginatedLocator locator) {
        if (this.paginated == null) {
            this.paginated = new ArrayList<>();
        }
        this.paginated.add(locator);
        return this;
    }

    // ---- spatial ----------------------------------------------------------

    public List<SpatialLocator> getSpatial() {
        return spatial;
    }

    public void setSpatial(List<SpatialLocator> spatial) {
        this.spatial = spatial;
    }

    public Locators addSpatial(SpatialLocator locator) {
        if (this.spatial == null) {
            this.spatial = new ArrayList<>();
        }
        this.spatial.add(locator);
        return this;
    }

    // ---- temporal ---------------------------------------------------------

    public List<TemporalLocator> getTemporal() {
        return temporal;
    }

    public void setTemporal(List<TemporalLocator> temporal) {
        this.temporal = temporal;
    }

    public Locators addTemporal(TemporalLocator locator) {
        if (this.temporal == null) {
            this.temporal = new ArrayList<>();
        }
        this.temporal.add(locator);
        return this;
    }

    /**
     * @return true if no locators of any type are present
     */
    public boolean isEmpty() {
        return (text == null || text.isEmpty())
                && (paginated == null || paginated.isEmpty())
                && (spatial == null || spatial.isEmpty())
                && (temporal == null || temporal.isEmpty());
    }
}
