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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ocr.tess4j.ImageDeskew;
import org.apache.tika.utils.SystemUtils;

class ImagePreprocessor implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ImagePreprocessor.class);
    private static final double MINIMUM_DESKEW_THRESHOLD = 1.0D;

    private final String fullImageMagickPath;

    ImagePreprocessor(String fullImageMagickPath) {
        this.fullImageMagickPath = fullImageMagickPath;
    }


    //this assumes that image magick is available
    void process(Path sourceFile, Path targFile, Metadata metadata, TesseractOCRConfig config)
            throws IOException {


        double angle = config.isApplyRotation() ? getAngle(sourceFile, metadata) : 0d;

        if (config.isEnableImagePreprocessing() || config.isApplyRotation() && angle != 0) {
            // process the image - parameter values can be set in TesseractOCRConfig.properties
            CommandLine commandLine = new CommandLine(fullImageMagickPath);
            if (SystemUtils.IS_OS_WINDOWS) {
                commandLine.addArgument("convert");
            }

            // Arguments for ImageMagick
            final List<String> density =
                    Arrays.asList("-density", Integer.toString(config.getDensity()));
            final List<String> depth = Arrays.asList("-depth", Integer.toString(config.getDepth()));
            final List<String> colorspace = Arrays.asList("-colorspace", config.getColorspace());
            final List<String> filter = Arrays.asList("-filter", config.getFilter());
            final List<String> resize = Arrays.asList("-resize", config.getResize() + "%");
            final List<String> rotate = Arrays.asList("-rotate", Double.toString(-angle));
            final List<String> sourceFileArg =
                    Collections.singletonList(sourceFile.toAbsolutePath().toString());
            final List<String> targFileArg =
                    Collections.singletonList(targFile.toAbsolutePath().toString());

            Stream<List<String>> stream = Stream.empty();
            if (angle == 0) {
                if (config.isEnableImagePreprocessing()) {
                    // Do pre-processing, but don't do any rotation
                    stream = Stream.of(density, depth, colorspace, filter, resize, sourceFileArg,
                            targFileArg);
                }
            } else if (config.isEnableImagePreprocessing()) {
                // Do pre-processing with rotation
                stream =
                        Stream.of(density, depth, colorspace, filter, resize, rotate, sourceFileArg,
                                targFileArg);

            } else if (config.isApplyRotation()) {
                // Just rotation
                stream = Stream.of(rotate, sourceFileArg, targFileArg);
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
            LOG.debug("Changing angle " + angle + " to 0.0");
            angle = 0d;
        } else {
            metadata.add(TesseractOCRParser.IMAGE_ROTATION,
                    String.format(Locale.getDefault(), "%.3f", angle));
        }

        return angle;
    }


}
