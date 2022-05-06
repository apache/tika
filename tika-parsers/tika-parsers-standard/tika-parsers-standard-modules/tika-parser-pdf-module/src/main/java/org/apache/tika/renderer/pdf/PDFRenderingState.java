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
package org.apache.tika.renderer.pdf;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.RenderingState;

public class PDFRenderingState extends RenderingState {

    private TikaInputStream tis;

    private RenderResults renderResults;

    public PDFRenderingState(TikaInputStream tis) {
        this.tis = tis;
    }

    public TikaInputStream getTikaInputStream() {
        return tis;
    }


    public void setRenderResults(RenderResults renderResults) {
        this.renderResults = renderResults;
    }

    public RenderResults getRenderResults() {
        return renderResults;
    }
}
