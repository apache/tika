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
package org.apache.tika.renderer.pdf.pdfiumtoppm;

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

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.exception.TikaTimeoutException;
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
 * Renderer that uses the {@code pdfiumtoppm} command
 * (<a href="https://github.com/tballison/pdfiumtoppm">github.com/tballison/pdfiumtoppm</a>),
 * a {@code pdftoppm}-compatible renderer built on PDFium, to convert PDF pages
 * to PNG images.
 * <p>
 * Compared with {@code pdftoppm} it renders faster with a shorter tail on
 * pathological files and adds hard limits for untrusted input:
 * {@code -max-pixels} (downscale only, never enlarge) and {@code -max-memory}
 * (an address-space limit the process enforces on itself; exit code 4 when
 * hit). The binary and a matching {@code libpdfium.so} ship together in the
 * release tarball; keep them in the same directory.
 * <p>
 * Configuration key: {@code "pdfiumtoppm-renderer"}
 *
 * @since Apache Tika 4.1
 */
@TikaComponent(name = "pdfiumtoppm-renderer", spi = false)
public class PdfiumToPpmRenderer implements Renderer {

    /** pdfiumtoppm exits 4 when a page could not fit under {@code -max-memory}. */
    static final int EXIT_MEMORY = 4;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("pdf"));

    private static final Pattern PAGE_FILE_PATTERN =
            Pattern.compile("tika-pdfium-(\\d+)\\.(png|ppm|pgm)");

    private String pdfiumToPpmPath = "pdfiumtoppm";
    private String pdfiumLibraryDir = null;
    private int dpi = 300;
    private boolean gray = true;

    /**
     * Write PNG (default) or, when false, binary PPM/PGM: no encode or decode, but no DPI
     * header either, so tell the OCR engine the resolution (tesseract: {@code user_defined_dpi}).
     */
    private boolean png = true;
    private int timeoutMillis = 120000;

    /**
     * Longest edge in pixels; maps to {@code -scale-to}. Like {@code pdftoppm},
     * this scales every page to exactly this size, enlarging small pages and
     * ignoring {@code dpi}. Off by default; {@link #maxPixels} is the cap.
     */
    private int maxScaleTo = -1;

    /**
     * Maximum width &times; height of a rendered page; maps to
     * {@code -max-pixels}. Pages that would exceed it are downscaled to fit;
     * smaller pages are untouched. Default 16,777,216 (4096 &times; 4096).
     */
    private long maxPixels = 4096L * 4096L;

    /**
     * Address-space limit in MiB for the render process; maps to
     * {@code -max-memory}. {@code -1} leaves pdfiumtoppm's own default
     * (4096 MiB or half of RAM, whichever is lower); {@code 0} disables the
     * limit.
     */
    private int maxMemoryMb = -1;

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
            renderRequest(path, parseContext, request, results, tmp);
        }
        return results;
    }

    private void renderRequest(Path pdf, ParseContext parseContext,
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
                builder, parseContext, timeoutMillis, 10, 1000);
        if (result.isTimeout()) {
            throw new TikaTimeoutException("pdfiumtoppm timed out",
                    result.getRequestedTimeoutMillis(), result.getGrantedTimeoutMillis());
        } else if (result.getExitValue() == EXIT_MEMORY) {
            throw new TikaMemoryLimitException(
                    "pdfiumtoppm hit its -max-memory limit: " + result.getStderr());
        } else if (result.getExitValue() != 0) {
            throw new TikaException(
                    "pdfiumtoppm failed (exit " + result.getExitValue()
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
        args.add(pdfiumToPpmPath);
        if (pdfiumLibraryDir != null) {
            args.add("-pdfium");
            args.add(ProcessUtils.escapeCommandLine(pdfiumLibraryDir));
        }
        if (png) {
            args.add("-png");
        }
        args.add("-r");
        args.add(String.valueOf(dpi));
        if (maxScaleTo > 0) {
            args.add("-scale-to");
            args.add(String.valueOf(maxScaleTo));
        }
        if (maxPixels > 0) {
            args.add("-max-pixels");
            args.add(String.valueOf(maxPixels));
        }
        if (maxMemoryMb >= 0) {
            args.add("-max-memory");
            args.add(String.valueOf(maxMemoryMb));
        }
        if (gray) {
            args.add("-gray");
        }
        if (request != PageRangeRequest.RENDER_ALL) {
            args.add("-f");
            args.add(String.valueOf(request.getFrom()));
            args.add("-l");
            args.add(String.valueOf(request.getTo()));
        }
        args.add(ProcessUtils.escapeCommandLine(
                pdf.toAbsolutePath().toString()));
        args.add(ProcessUtils.escapeCommandLine(
                dir.toAbsolutePath().toString() + "/tika-pdfium"));
        return args.toArray(new String[0]);
    }

    // ---- config getters/setters -------------------------------------------

    public String getPdfiumToPpmPath() {
        return pdfiumToPpmPath;
    }

    /**
     * Path to the {@code pdfiumtoppm} executable. Defaults to
     * {@code "pdfiumtoppm"} (on the system path).
     */
    public void setPdfiumToPpmPath(String pdfiumToPpmPath) {
        this.pdfiumToPpmPath = pdfiumToPpmPath;
    }

    public String getPdfiumLibraryDir() {
        return pdfiumLibraryDir;
    }

    /**
     * Directory containing {@code libpdfium.so}; maps to {@code -pdfium}.
     * Optional: by default pdfiumtoppm looks in {@code $PDFIUM_PATH}, its
     * own directory, then the system library path.
     */
    public void setPdfiumLibraryDir(String pdfiumLibraryDir) {
        this.pdfiumLibraryDir = pdfiumLibraryDir;
    }

    public int getDpi() {
        return dpi;
    }

    /**
     * Rendering resolution in DPI. Defaults to 300. Ignored when
     * {@link #setMaxScaleTo(int)} is set.
     */
    public void setDpi(int dpi) {
        if (dpi < 1) {
            throw new IllegalArgumentException("dpi must be at least 1, got: " + dpi);
        }
        this.dpi = dpi;
    }

    public boolean isPng() {
        return png;
    }

    /**
     * If true (the default), pages are written as PNG. If false, as binary PPM (PGM with
     * {@link #setGray(boolean)}), which skips PNG encode/decode; the images then carry no DPI,
     * so set the OCR engine's resolution explicitly.
     */
    public void setPng(boolean png) {
        this.png = png;
    }

    public boolean isGray() {
        return gray;
    }

    /**
     * If true (the default), render in grayscale.
     */
    public void setGray(boolean gray) {
        this.gray = gray;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Timeout in milliseconds for the pdfiumtoppm process. Defaults to
     * 120000 (2 minutes). pdfiumtoppm bounds memory but not time; this is
     * the only time limit.
     */
    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public int getMaxScaleTo() {
        return maxScaleTo;
    }

    /**
     * Scale every page's longest edge to exactly this many pixels
     * ({@code -scale-to}); enlarges small pages and overrides {@code dpi}.
     * {@code -1} (the default) disables it. Prefer {@link #setMaxPixels(long)}
     * for a cap that never enlarges.
     */
    public void setMaxScaleTo(int maxScaleTo) {
        if (maxScaleTo < 1 && maxScaleTo != -1) {
            throw new IllegalArgumentException(
                    "maxScaleTo must be -1 (disabled) or at least 1, got: "
                            + maxScaleTo);
        }
        this.maxScaleTo = maxScaleTo;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    /**
     * Maximum width &times; height of a rendered page ({@code -max-pixels});
     * larger pages are downscaled to fit. Default 16,777,216. {@code -1}
     * disables it (not recommended).
     */
    public void setMaxPixels(long maxPixels) {
        if (maxPixels < 1 && maxPixels != -1) {
            throw new IllegalArgumentException(
                    "maxPixels must be -1 (disabled) or at least 1, got: " + maxPixels);
        }
        this.maxPixels = maxPixels;
    }

    public int getMaxMemoryMb() {
        return maxMemoryMb;
    }

    /**
     * Address-space limit in MiB for the render process ({@code -max-memory}).
     * {@code -1} (the default) keeps pdfiumtoppm's own default of 4096 MiB or
     * half of RAM, whichever is lower; {@code 0} disables the limit. When the
     * limit is hit the render fails with a {@link TikaMemoryLimitException}.
     */
    public void setMaxMemoryMb(int maxMemoryMb) {
        if (maxMemoryMb < -1) {
            throw new IllegalArgumentException(
                    "maxMemoryMb must be -1 (tool default), 0 (no limit) or positive, got: "
                            + maxMemoryMb);
        }
        this.maxMemoryMb = maxMemoryMb;
    }
}
