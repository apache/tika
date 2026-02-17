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
package org.apache.tika.renderer.pdf.poppler;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
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

/**
 * Renderer that uses Poppler's {@code pdftoppm} command to convert PDF
 * pages to PNG images.
 * <p>
 * Poppler is pre-installed on most Linux distributions and is the
 * fastest widely-available PDF renderer. On macOS it can be installed
 * via {@code brew install poppler}; on Windows via MSYS2 or Chocolatey.
 * <p>
 * Configuration key: {@code "poppler-renderer"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "poppler-renderer", spi = false)
public class PopplerRenderer implements Renderer {

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("pdf"));

    /**
     * Matches the Poppler output pattern: {@code prefix-01.png},
     * {@code prefix-02.png}, etc.
     */
    private static final Pattern PAGE_FILE_PATTERN =
            Pattern.compile("tika-poppler-(\\d+)\\.png");

    private String pdftoppmPath = "pdftoppm";
    private int dpi = 300;
    private boolean gray = true;
    private int timeoutMs = 120000;

    /**
     * Maximum pixel dimension (in pixels) for the longest edge of a rendered
     * page image. Maps to pdftoppm's {@code -scale-to} flag.
     * <p>
     * If a PDF page would render larger than this value (in pixels) at the
     * configured DPI, pdftoppm scales the output image down so that its
     * longest edge equals {@code maxScaleTo} pixels, preserving the aspect
     * ratio. For example, with {@code maxScaleTo=4096}, a landscape page
     * that would normally render to 6000&times;4000 pixels is scaled to
     * 4096&times;2731 pixels instead.
     * <p>
     * If the rendered image is already smaller than {@code maxScaleTo}
     * on both edges, no scaling is applied — the image is not enlarged.
     * <p>
     * This is the primary defense against pathologically large PDF pages
     * (e.g., architectural drawings, maps, posters) that would otherwise
     * produce multi-gigabyte images and cause OOM.
     * <p>
     * Default is 4096 pixels. Set to {@code -1} to disable scaling
     * (not recommended).
     */
    private int maxScaleTo = 4096;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public RenderResults render(TikaInputStream tis, Metadata metadata,
                                ParseContext parseContext,
                                RenderRequest... requests)
            throws IOException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        PageBasedRenderResults results = new PageBasedRenderResults(tmp);
        Path path = tis.getPath();
        for (RenderRequest request : requests) {
            renderRequest(path, metadata, parseContext, request, results, tmp);
        }
        return results;
    }

    private void renderRequest(Path pdf, Metadata metadata,
                               ParseContext parseContext,
                               RenderRequest request,
                               PageBasedRenderResults results,
                               TemporaryResources tmp)
            throws TikaException, IOException {
        if (!(request instanceof PageRangeRequest)) {
            throw new TikaException(
                    "I regret that this renderer can only handle "
                            + "PageRangeRequests, not " + request.getClass());
        }
        PageRangeRequest rangeRequest = (PageRangeRequest) request;

        RenderingTracker tracker = parseContext.get(RenderingTracker.class);
        if (tracker == null) {
            tracker = new RenderingTracker();
            parseContext.set(RenderingTracker.class, tracker);
        }

        Path dir = Files.createTempDirectory("tika-render-");
        tmp.addResource(new Closeable() {
            @Override
            public void close() throws IOException {
                Files.delete(dir);
            }
        });

        String[] args = createCommandLine(pdf, dir, rangeRequest);

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(args);
        FileProcessResult result = ProcessUtils.execute(
                builder, timeoutMs, 10, 1000);
        if (result.getExitValue() != 0) {
            throw new TikaException(
                    "pdftoppm failed (exit " + result.getExitValue()
                            + "): " + result.getStderr());
        }

        Matcher m = PAGE_FILE_PATTERN.matcher("");
        File[] files = dir.toFile().listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (m.reset(f.getName()).find()) {
                int pageNumber = Integer.parseInt(m.group(1));
                Metadata renderMetadata = Metadata.newInstance(parseContext);
                renderMetadata.set(TikaPagedText.PAGE_NUMBER, pageNumber);
                renderMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        TikaCoreProperties.EmbeddedResourceType.RENDERING
                                .name());
                results.add(new RenderResult(
                        RenderResult.STATUS.SUCCESS,
                        tracker.getNextId(),
                        f.toPath(),
                        renderMetadata));
            }
        }
    }

    String[] createCommandLine(Path pdf, Path dir,
                               PageRangeRequest request) {
        List<String> args = new ArrayList<>();
        args.add(pdftoppmPath);

        // Output format
        args.add("-png");

        // Resolution
        args.add("-r");
        args.add(String.valueOf(dpi));

        // Scale cap — prevents OOM on huge pages
        if (maxScaleTo > 0) {
            args.add("-scale-to");
            args.add(String.valueOf(maxScaleTo));
        }

        // Colorspace
        if (gray) {
            args.add("-gray");
        }

        // Page range
        if (request != PageRangeRequest.RENDER_ALL) {
            args.add("-f");
            args.add(String.valueOf(request.getFrom()));
            args.add("-l");
            args.add(String.valueOf(request.getTo()));
        }

        // Input PDF
        args.add(ProcessUtils.escapeCommandLine(
                pdf.toAbsolutePath().toString()));

        // Output prefix (pdftoppm appends -NN.png)
        args.add(ProcessUtils.escapeCommandLine(
                dir.toAbsolutePath().toString() + "/tika-poppler"));

        return args.toArray(new String[0]);
    }

    // ---- config getters/setters -------------------------------------------

    public String getPdftoppmPath() {
        return pdftoppmPath;
    }

    /**
     * Set the path to the {@code pdftoppm} executable. Defaults to
     * {@code "pdftoppm"} (assumes it is on the system path).
     */
    public void setPdftoppmPath(String pdftoppmPath) {
        this.pdftoppmPath = pdftoppmPath;
    }

    public int getDpi() {
        return dpi;
    }

    /**
     * Set the rendering resolution in DPI. Defaults to 300.
     */
    public void setDpi(int dpi) {
        this.dpi = dpi;
    }

    public boolean isGray() {
        return gray;
    }

    /**
     * If true (the default), render in grayscale. Set to false for
     * full-color rendering.
     */
    public void setGray(boolean gray) {
        this.gray = gray;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Set the timeout in milliseconds for the pdftoppm process.
     * Defaults to 120000 (2 minutes).
     */
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxScaleTo() {
        return maxScaleTo;
    }

    /**
     * Set the maximum pixel dimension (in pixels) for the longest edge
     * of rendered page images. Maps to pdftoppm's {@code -scale-to} flag.
     * Pages that would render smaller than this are not enlarged.
     * <p>
     * Default is 4096 pixels. Set to {@code -1} to disable (not recommended).
     */
    public void setMaxScaleTo(int maxScaleTo) {
        if (maxScaleTo < 1 && maxScaleTo != -1) {
            throw new IllegalArgumentException(
                    "maxScaleTo must be -1 (disabled) or at least 1, got: "
                            + maxScaleTo);
        }
        this.maxScaleTo = maxScaleTo;
    }
}
