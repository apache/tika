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
package org.apache.tika.pipes.reporters;

import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Base class that includes filtering by {@link PipesResult.STATUS}
 */
public abstract class PipesReporterBase extends AbstractTikaExtension implements PipesReporter {


    private StatusFilter statusFilter;

    public PipesReporterBase(ExtensionConfig pluginConfig, Set<String> includes, Set<String> excludes) throws TikaConfigException {
        super(pluginConfig);
        statusFilter = buildStatusFilter(includes, excludes);
    }


    private StatusFilter buildStatusFilter(Set<String> includes,
                                           Set<String> excludes) throws TikaConfigException {
        if (includes == null && excludes == null) {
            return new AcceptAllFilter();
        }
        if (includes == null) {
            includes = Set.of();
        }
        if (excludes == null) {
            excludes = Set.of();
        }
        if (! includes.isEmpty() && ! excludes.isEmpty()) {
            throw new TikaConfigException("Only one of includes and excludes may have any " +
                    "contents");
        }
        if (! includes.isEmpty()) {
            return new IncludesFilter(includes);
        } else if (!excludes.isEmpty()) {
            return new ExcludesFilter(excludes);
        }
        return new AcceptAllFilter();
    }


    /**
     * Implementations must call this for the includes/excludes filters to work!
     * @param status
     * @return
     */
    public boolean accept(PipesResult.STATUS status) {
        return statusFilter.accept(status);
    }

    private abstract static class StatusFilter {
        abstract boolean accept(PipesResult.STATUS status);
    }

    private static class IncludesFilter extends StatusFilter {
        private final Set<PipesResult.STATUS> includes;

        private IncludesFilter(Set<String> includesStrings) {
            this.includes = convert(includesStrings);
        }

        @Override
        boolean accept(PipesResult.STATUS status) {
            return includes.contains(status);
        }
    }

    private static class ExcludesFilter extends StatusFilter {
        private final Set<PipesResult.STATUS> excludes;

        ExcludesFilter(Set<String> excludes) {
            this.excludes = convert(excludes);
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

    private static Set<PipesResult.STATUS> convert(Set<String> statusStrings) {
        Set<PipesResult.STATUS> ret = new HashSet<>();
        for (String s : statusStrings) {
            ret.add(PipesResult.STATUS.valueOf(s));
        }
        return ret;
    }


}
