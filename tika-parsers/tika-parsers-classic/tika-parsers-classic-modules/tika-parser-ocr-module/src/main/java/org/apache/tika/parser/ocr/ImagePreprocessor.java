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
package org.apache.tika.parser.ocr;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.ocr.tess4j.ImageDeskew;
import org.apache.tika.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

class ImagePreprocessor {
    private static final Map<String, Boolean> IMAGE_MAGICK_PRESENT = new HashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(TesseractOCRParser.class);
    private static final double MINIMUM_DESKEW_THRESHOLD = 1.0D;

    public static boolean hasImageMagick(TesseractOCRConfig config) {
        // Fetch where the config says to find ImageMagick Program
        String ImageMagick = getImageMagickPath(config);

        // Have we already checked for a copy of ImageMagick Program there?
        if (IMAGE_MAGICK_PRESENT.containsKey(ImageMagick)) {
            return IMAGE_MAGICK_PRESENT.get(ImageMagick);
        }
        //prevent memory bloat
        if (IMAGE_MAGICK_PRESENT.size() > 100) {
            IMAGE_MAGICK_PRESENT.clear();
        }
        //check that directory exists
        if (!config.getImageMagickPath().isEmpty() &&
                ! Files.isDirectory(Paths.get(config.getImageMagickPath()))) {
            IMAGE_MAGICK_PRESENT.put(ImageMagick, false);
            return false;
        }

        // Try running ImageMagick program from there, and see if it exists + works
        String[] checkCmd = { ImageMagick };
        boolean hasImageMagick = ExternalParser.check(checkCmd);
        if (!hasImageMagick) {
            LOG.warn("ImageMagick does not appear to be installed " +
                    "(commandline: "+ImageMagick+")");
        }
        IMAGE_MAGICK_PRESENT.put(ImageMagick, hasImageMagick);

        return hasImageMagick;
    }


    private static String getImageMagickPath(TesseractOCRConfig config) {
        return config.getImageMagickPath() + getImageMagickProg();
    }


    //this assumes that image magick is available
    void process(Path sourceFile, Path targFile, Metadata metadata,
                 TesseractOCRConfig config) throws IOException {


        double angle = config.isApplyRotation()
                ? getAngle(sourceFile, metadata)
                : 0d;

        if (config.isEnableImageProcessing() || config.isApplyRotation() && angle != 0) {
            // process the image - parameter values can be set in TesseractOCRConfig.properties
            CommandLine commandLine = new CommandLine(getImageMagickPath(config));
            if (System.getProperty("os.name").startsWith("Windows")) {
                commandLine.addArgument("convert");
            }

            // Arguments for ImageMagick
            final List<String> density = Arrays.asList("-density", Integer.toString(config.getDensity()));
            final List<String> depth = Arrays.asList("-depth", Integer.toString(config.getDepth()));
            final List<String> colorspace = Arrays.asList("-colorspace", config.getColorspace());
            final List<String> filter = Arrays.asList("-filter", config.getFilter());
            final List<String> resize = Arrays.asList("-resize", config.getResize() + "%");
            final List<String> rotate = Arrays.asList("-rotate", Double.toString(-angle));
            final List<String> sourceFileArg = Collections.singletonList(sourceFile.toAbsolutePath().toString());
            final List<String> targFileArg = Collections.singletonList(targFile.toAbsolutePath().toString());

            Stream<List<String>> stream = Stream.empty();
            if (angle == 0) {
                if (config.isEnableImageProcessing()) {
                    // Do pre-processing, but don't do any rotation
                    stream = Stream.of(
                            density,
                            depth,
                            colorspace,
                            filter,
                            resize,
                            sourceFileArg,
                            targFileArg);
                }
            } else if (config.isEnableImageProcessing()) {
                // Do pre-processing with rotation
                stream = Stream.of(
                        density,
                        depth,
                        colorspace,
                        filter,
                        resize,
                        rotate,
                        sourceFileArg,
                        targFileArg);

            } else if (config.isApplyRotation()) {
                // Just rotation
                stream = Stream.of(
                        rotate,
                        sourceFileArg,
                        targFileArg);
            }
            final String[] args = stream.flatMap(Collection::stream).toArray(String[]::new);
            commandLine.addArguments(args, true);
            DefaultExecutor executor = new DefaultExecutor();
            try {
                executor.execute(commandLine);
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn("ImageMagick failed (commandline: " + commandLine + ")", e);
            }
            metadata.add(TesseractOCRParser.IMAGE_MAGICK, "true");
        }
    }

    /**
     * Get the current skew angle of the image.  Positive = clockwise; Negative = counter-clockwise
     */
    private double getAngle(Path sourceFile, Metadata metadata) throws IOException {
        BufferedImage bi = ImageIO.read(sourceFile.toFile());
        ImageDeskew id = new ImageDeskew(bi);
        double angle = id.getSkewAngle();

        if (angle < MINIMUM_DESKEW_THRESHOLD && angle > -MINIMUM_DESKEW_THRESHOLD) {
            LOG.debug("Changing angle " + angle  + " to 0.0");
            angle = 0d;
        } else {
            metadata.add(TesseractOCRParser.IMAGE_ROTATION, String.format(Locale.getDefault(), "%.3f", angle));
        }

        return angle;
    }

    public static String getImageMagickProg() {
        return System.getProperty("os.name").startsWith("Windows") ?
                "magick" : "convert";
    }
}
