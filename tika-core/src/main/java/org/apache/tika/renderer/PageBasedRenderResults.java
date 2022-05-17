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
package org.apache.tika.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.TikaPagedText;

public class PageBasedRenderResults extends RenderResults {

    Map<Integer, List<RenderResult>> results = new HashMap<>();

    public PageBasedRenderResults(TemporaryResources tmp) {
        super(tmp);
    }
    public void add(RenderResult result) {
        Integer page = result.getMetadata().getInt(TikaPagedText.PAGE_NUMBER);
        if (page != null) {
            List<RenderResult> pageResults = results.get(page);
            if (pageResults == null) {
                pageResults = new ArrayList<>();
                results.put(page, pageResults);
            }
            pageResults.add(result);
        }
        super.add(result);
    }

    public List<RenderResult> getPage(int pageNumber) {
        return results.get(pageNumber);
    }
}
