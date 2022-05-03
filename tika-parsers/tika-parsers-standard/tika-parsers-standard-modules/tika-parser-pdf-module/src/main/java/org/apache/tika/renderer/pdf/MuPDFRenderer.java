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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.RenderingTracker;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

public class MuPDFRenderer implements Renderer {

    Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("pdf"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext,
                                RenderRequest... requests) throws IOException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        PageBasedRenderResults results = new PageBasedRenderResults(tmp);
        Path path = TikaInputStream.get(is, tmp).getPath();
        for (RenderRequest request : requests) {
            renderRequest(path, metadata, parseContext, request, results, tmp);
        }
        return results;
    }

    private RenderResults renderRequest(Path pdf, Metadata metadata, ParseContext parseContext,
                                        RenderRequest request, RenderResults results,
                                        TemporaryResources tmp) throws TikaException, IOException {
        if (! (request instanceof PageRangeRequest)) {
            throw new TikaException("I regret that this renderer can only handle " +
                    "PageRangeRequests, not " + request.getClass());
        }
        PageRangeRequest rangeRequest = (PageRangeRequest)request;
        RenderingTracker tracker = parseContext.get(RenderingTracker.class);
        if (tracker == null) {
            tracker = new RenderingTracker();
            parseContext.set(RenderingTracker.class, tracker);
        }

        Path dir = Files.createTempDirectory("tika-render-");
        //TODO -- this assumes files have been deleted first
        //do something smarter
        tmp.addResource(new Closeable() {
            @Override
            public void close() throws IOException {
                Files.delete(dir);
            }
        });
        //TODO -- run mutool pages to get page sizes
        //and then use that information in the -O to get proper scaling
        //etc.
        // This would also allow us to run on a single page at a time if that's of any interest
        String[] args = createCommandLine(pdf, dir, rangeRequest);

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(args);
        //TODO: parameterize timeout
        FileProcessResult result = ProcessUtils.execute(builder, 60000, 10, 1000);
        if (result.getExitValue() != 0) {
            throw new TikaException(result.getStderr());
        }
        //TODO -- fix this
        Matcher m = Pattern.compile("tika-mutool-render-(\\d+)\\.png").matcher("");
        for (File f : dir.toFile().listFiles()) {
            String n = f.getName();
            if (m.reset(n).find()) {
                int pageIndex = Integer.parseInt(m.group(1));
                Metadata renderMetadata = new Metadata();
                renderMetadata.set(Rendering.PAGE_NUMBER, pageIndex);
                renderMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
                results.add(new RenderResult(RenderResult.STATUS.SUCCESS, tracker.getNextId(),
                        f.toPath(), renderMetadata));
            }
        }

        return results;
    }

    private String[] createCommandLine(Path pdf, Path dir, PageRangeRequest request) {
        //TODO parameterize all the things; mutool path, colorspace and size and format and...
        List<String> args = new ArrayList<>();
        args.add("mutool");
        args.add("convert");
        args.add("-O colorspace=gray");
        args.add("-o");
        args.add(
                ProcessUtils.escapeCommandLine(
                        dir.toAbsolutePath().toString() + "/" + "tika-mutool-render-%d.png"));
        args.add(ProcessUtils.escapeCommandLine(pdf.toAbsolutePath().toString()));
        if (request != PageRangeRequest.RENDER_ALL) {
            StringBuilder sb = new StringBuilder();
            int cnt = 0;
            for (int i = request.getFrom(); i <= request.getTo(); i++) {
                if (cnt++ > 0) {
                    sb.append(",");
                }
                sb.append(i);
            }
            args.add(sb.toString());
        }
        return args.toArray(new String[0]);
    }
}
