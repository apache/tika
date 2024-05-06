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
package org.apache.tika.pipes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;

/** Base class that includes filtering by {@link PipesResult.STATUS} */
public abstract class PipesReporterBase extends PipesReporter implements Initializable {

    private final Set<PipesResult.STATUS> includes = new HashSet<>();
    private final Set<PipesResult.STATUS> excludes = new HashSet<>();

    private StatusFilter statusFilter;

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        statusFilter = buildStatusFilter(includes, excludes);
    }

    private StatusFilter buildStatusFilter(
            Set<PipesResult.STATUS> includes, Set<PipesResult.STATUS> excludes)
            throws TikaConfigException {
        if (includes.size() > 0 && excludes.size() > 0) {
            throw new TikaConfigException(
                    "Only one of includes and excludes may have any " + "contents");
        }
        if (includes.size() > 0) {
            return new IncludesFilter(includes);
        } else if (excludes.size() > 0) {
            return new ExcludesFilter(excludes);
        }
        return new AcceptAllFilter();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {}

    /**
     * Implementations must call this for the includes/excludes filters to work!
     *
     * @param status
     * @return
     */
    public boolean accept(PipesResult.STATUS status) {
        return statusFilter.accept(status);
    }

    @Field
    public void setIncludes(List<String> includes) throws TikaConfigException {
        for (String s : includes) {
            try {
                PipesResult.STATUS status = PipesResult.STATUS.valueOf(s);
                this.includes.add(status);
            } catch (IllegalArgumentException e) {
                String optionString = getOptionString();
                throw new TikaConfigException(
                        "I regret I don't recognize " + s + ". I only understand: " + optionString,
                        e);
            }
        }
    }

    @Field
    public void setExcludes(List<String> excludes) throws TikaConfigException {
        for (String s : excludes) {
            try {
                PipesResult.STATUS status = PipesResult.STATUS.valueOf(s);
                this.excludes.add(status);
            } catch (IllegalArgumentException e) {
                String optionString = getOptionString();
                throw new TikaConfigException(
                        "I regret I don't recognize " + s + ". I only understand: " + optionString,
                        e);
            }
        }
    }

    private String getOptionString() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (PipesResult.STATUS status : PipesResult.STATUS.values()) {
            if (++i > 1) {
                sb.append(", ");
            }
            sb.append(status.name());
        }
        return sb.toString();
    }

    private abstract static class StatusFilter {
        abstract boolean accept(PipesResult.STATUS status);
    }

    private static class IncludesFilter extends StatusFilter {
        private final Set<PipesResult.STATUS> includes;

        private IncludesFilter(Set<PipesResult.STATUS> includes) {
            this.includes = includes;
        }

        @Override
        boolean accept(PipesResult.STATUS status) {
            return includes.contains(status);
        }
    }

    private static class ExcludesFilter extends StatusFilter {
        private final Set<PipesResult.STATUS> excludes;

        ExcludesFilter(Set<PipesResult.STATUS> excludes) {
            this.excludes = excludes;
        }

        @Override
        boolean accept(PipesResult.STATUS status) {
            return !excludes.contains(status);
        }
    }

    private static class AcceptAllFilter extends StatusFilter {

        @Override
        boolean accept(PipesResult.STATUS status) {
            return true;
        }
    }
}
