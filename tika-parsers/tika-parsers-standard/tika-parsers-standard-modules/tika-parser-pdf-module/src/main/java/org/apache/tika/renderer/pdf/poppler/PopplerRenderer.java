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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.ImageType;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.RenderSettings;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.RenderingTracker;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

/**
 * Renders PDF pages through Poppler's {@code pdftoppm}, one call per page at the dpi the
 * {@link RenderSettings} the parse scoped (else this renderer's own) resolve to for that page:
 * the target dpi, lowered so the page fits the box and its long side stays within
 * {@code maxScaleTo}, never raised. A page over {@code maxImagePixels} is skipped with a
 * warning. Page sizes come from the {@link PDDocument} the parser leaves as the stream's open
 * container, else from a load of the file.
 * <p>
 * Poppler is pre-installed on most Linux distributions and is the fastest widely-available
 * PDF renderer. On macOS it can be installed via {@code brew install poppler}; on Windows via
 * MSYS2 or Chocolatey.
 * <p>
 * Configuration key: {@code "poppler-renderer"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "poppler-renderer", spi = false)
public class PopplerRenderer implements Renderer {

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("pdf"));

    /** pdftoppm names its output {@code prefix-N.png} (zero-padded) or .jpg / .tif. */
    private static final Pattern PAGE_FILE_PATTERN =
            Pattern.compile("tika-poppler-(\\d+)\\.(png|jpg|tif)");

    private String pdftoppmPath = "pdftoppm";
    private int timeoutMillis = 120000;

    /** What this renderer draws when the parse scopes no settings: 300 dpi gray. */
    private final RenderSettings defaults = RenderSettings.defaults();

    /**
     * Longest side of a rendered page, in pixels; a page that would be longer at the dpi is
     * rendered smaller. The guard against posters and maps that would otherwise be gigabytes
     * of pixels. 4096 by default; -1 for none (not recommended).
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
        PDDocument open = tis.getOpenContainer() instanceof PDDocument d ? d : null;
        try (PDDocument loaded = open == null ? Loader.loadPDF(path.toFile()) : null) {
            PDDocument document = open == null ? loaded : open;
            Path dir = Files.createTempDirectory("tika-render-");
            tmp.addResource((Closeable) () -> Files.delete(dir));
            for (RenderRequest request : requests) {
                if (!(request instanceof PageRangeRequest range)) {
                    throw new TikaException("I regret that this renderer can only handle "
                            + "PageRangeRequests, not " + request.getClass());
                }
                int last = document.getNumberOfPages();
                int from = range == PageRangeRequest.RENDER_ALL ? 1 : range.getFrom();
                int to = range == PageRangeRequest.RENDER_ALL ? last : Math.min(range.getTo(), last);
                for (int page = from; page <= to; page++) {
                    results.add(renderPage(path, dir, document, page, parseContext));
                }
            }
        } catch (Throwable t) {
            // results never reach the caller; nothing else would delete the pages
            try {
                results.close();
            } catch (IOException e) {
                t.addSuppressed(e);
            }
            throw t;
        }
        return results;
    }

    private RenderResult renderPage(Path pdf, Path dir, PDDocument document, int page,
                                    ParseContext parseContext) throws IOException, TikaException {
        RenderingTracker tracker = parseContext.get(RenderingTracker.class);
        if (tracker == null) {
            tracker = new RenderingTracker();
            parseContext.set(RenderingTracker.class, tracker);
        }
        int id = tracker.getNextId();
        Metadata m = Metadata.newInstance(parseContext);
        m.set(TikaPagedText.PAGE_NUMBER, page);
        m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
        RenderSettings settings = settings(parseContext);
        PDRectangle mediaBox = document.getPage(page - 1).getMediaBox();
        double dpi = dpi(settings, mediaBox.getWidth(), mediaBox.getHeight());
        long pixels = RenderSettings.estimatedPixels(mediaBox.getWidth(), mediaBox.getHeight(), dpi);
        if (settings.exceedsMaxPixels(pixels)) {
            EmbeddedDocumentUtil.recordException(new IOException("page " + page
                    + " would render to " + pixels + " pixels at " + dpi
                    + " dpi, above maxImagePixels " + settings.getMaxImagePixels()),
                    m, parseContext);
            return new RenderResult(RenderResult.STATUS.EXCEPTION, id, null, m);
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(createCommandLine(pdf, dir, page, dpi, settings));
        FileProcessResult result = ProcessUtils.execute(
                builder, parseContext, timeoutMillis, 10, 1000);
        if (result.isTimeout()) {
            throw new TikaTimeoutException("pdftoppm timed out",
                    result.getRequestedTimeoutMillis(), result.getGrantedTimeoutMillis());
        } else if (result.getExitValue() != 0) {
            throw new TikaException("pdftoppm failed (exit " + result.getExitValue()
                    + "): " + result.getStderr());
        }
        Matcher matcher = PAGE_FILE_PATTERN.matcher("");
        File[] files = dir.toFile().listFiles();
        for (File f : files == null ? new File[0] : files) {
            if (matcher.reset(f.getName()).find() && Integer.parseInt(matcher.group(1)) == page) {
                return new RenderResult(RenderResult.STATUS.SUCCESS, id, f.toPath(), m);
            }
        }
        throw new TikaException("pdftoppm wrote no image for page " + page + ": "
                + result.getStderr());
    }

    /** The target dpi, lowered so the page fits the box and {@code maxScaleTo}, never raised. */
    double dpi(RenderSettings settings, double widthPoints, double heightPoints) {
        double dpi = settings.effectiveDpi(widthPoints, heightPoints);
        double longSide = Math.max(widthPoints, heightPoints) / 72.0 * dpi;
        if (maxScaleTo > 0 && longSide > maxScaleTo) {
            dpi *= maxScaleTo / longSide;
        }
        return dpi;
    }

    String[] createCommandLine(Path pdf, Path dir, int page, double dpi, RenderSettings settings) {
        List<String> args = new ArrayList<>();
        args.add(pdftoppmPath);
        switch (settings.getImageFormat()) {
            case JPEG:
                args.add("-jpeg");
                args.add("-jpegopt");
                args.add("quality=" + Math.round(settings.getImageQuality() * 100));
                break;
            case TIFF:
                args.add("-tiff");
                break;
            default:
                args.add("-png");
                break;
        }
        args.add("-r");
        args.add(String.format(Locale.ROOT, "%.2f", dpi));
        if (settings.getImageType() == ImageType.GRAY) {
            args.add("-gray");
        }
        args.add("-f");
        args.add(String.valueOf(page));
        args.add("-l");
        args.add(String.valueOf(page));
        args.add(ProcessUtils.escapeCommandLine(pdf.toAbsolutePath().toString()));
        args.add(ProcessUtils.escapeCommandLine(dir.toAbsolutePath().toString() + "/tika-poppler"));
        return args.toArray(new String[0]);
    }

    /** The settings the parser scoped around this render, else this renderer's own. */
    private RenderSettings settings(ParseContext parseContext) {
        RenderSettings scoped = parseContext.get(RenderSettings.class);
        return scoped == null ? defaults : defaults.over(scoped);
    }

    // ---- config getters/setters -------------------------------------------

    public String getPdftoppmPath() {
        return pdftoppmPath;
    }

    /** The {@code pdftoppm} executable; {@code "pdftoppm"} (on the path) by default. */
    public void setPdftoppmPath(String pdftoppmPath) {
        this.pdftoppmPath = pdftoppmPath;
    }

    public int getDpi() {
        return defaults.getDpi();
    }

    /** The resolution when no parse scopes one. 300 by default. */
    public void setDpi(int dpi) {
        defaults.setDpi(dpi);
    }

    public boolean isGray() {
        return defaults.getImageType() == ImageType.GRAY;
    }

    /** Grayscale (the default) or colour, when no parse scopes an image type. */
    public void setGray(boolean gray) {
        defaults.setImageType(gray ? ImageType.GRAY : ImageType.RGB);
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    /** Timeout for one {@code pdftoppm} call (one page); 120000 by default. */
    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public int getMaxScaleTo() {
        return maxScaleTo;
    }

    /** Longest side of a rendered page in pixels; 4096 by default, -1 for none. */
    public void setMaxScaleTo(int maxScaleTo) {
        if (maxScaleTo < 1 && maxScaleTo != -1) {
            throw new IllegalArgumentException(
                    "maxScaleTo must be -1 (disabled) or at least 1, got: "
                            + maxScaleTo);
        }
        this.maxScaleTo = maxScaleTo;
    }
}
