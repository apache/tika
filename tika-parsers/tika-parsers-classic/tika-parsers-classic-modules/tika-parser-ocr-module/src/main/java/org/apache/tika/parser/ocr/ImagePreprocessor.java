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
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class ImagePreprocessor {
    private static final Map<String,Boolean> IMAGE_MAGICK_PRESENT = new HashMap<>();
    private static final Map<String, Boolean> PYTHON_PRESENT = new HashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(TesseractOCRParser.class);

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

    private static String getPythonPath(TesseractOCRConfig config) {
        return config.getPythonPath()+getPythonProg();
    }

    public static boolean hasPython(TesseractOCRConfig config) {
        String pythonPath = getPythonPath(config);
        if (PYTHON_PRESENT.containsKey(pythonPath)) {
            return PYTHON_PRESENT.get(pythonPath);
        }
        //prevent memory bloat
        if (PYTHON_PRESENT.size() > 100) {
            PYTHON_PRESENT.clear();
        }
        //check that directory exists
        if (!config.getPythonPath().isEmpty() &&
                ! Files.isDirectory(Paths.get(config.getPythonPath()))) {
            PYTHON_PRESENT.put(pythonPath, false);
            return false;
        }

        // check if python is installed and it has the required dependencies for the rotation program to run
        boolean hasPython = false;

        String[] checkCmd = { pythonPath, "--version" };
        boolean hasPythonExecutable = ExternalParser.check(checkCmd);
        if (! hasPythonExecutable) {
            LOG.warn("couldn't run python executable ("+
                    pythonPath+")");
            PYTHON_PRESENT.put(pythonPath, hasPythonExecutable);
            return hasPythonExecutable;
        }

        TemporaryResources tmp = null;
        File importCheck = null;
        try {
            tmp = new TemporaryResources();
            importCheck = tmp.createTemporaryFile();
            String prg = "from skimage.transform import radon\n" +
                    "from PIL import Image\n" + "" +
                    "import numpy\n";
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(importCheck), Charset.forName("UTF-8"));
            out.write(prg);
            out.flush();
            out.close();
        } catch (IOException e) {
            LOG.warn("Error writing file to test correct libs are available", e);
            hasPython = false;
            PYTHON_PRESENT.put(pythonPath, hasPython);
            return hasPython;
        }

        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{
                    pythonPath,
                    ProcessUtils.escapeCommandLine(importCheck.getAbsolutePath())});
            boolean completed = p.waitFor(30, TimeUnit.SECONDS);
            hasPython = completed;
            if (! completed) {
                LOG.warn("python3 did not successfully complete after 30 seconds");
                LOG.warn("rotation.py cannot be called");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("python3 ("+
                            pythonPath+ ") is not installed with the required dependencies: scikit-image and numpy",
                    e);
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
            IOUtils.closeQuietly(tmp);
        }
        PYTHON_PRESENT.put(pythonPath, hasPython);
        return hasPython;
    }

    //this assumes that image magick is available
    void process(Path sourceFile, Path targFile, Metadata metadata,
                 TesseractOCRConfig config) throws TikaException, IOException {

        String angle = getAngle(sourceFile, metadata, config);

        // process the image - parameter values can be set in TesseractOCRConfig.properties
        CommandLine commandLine = new CommandLine(getImageMagickPath(config));
        if (System.getProperty("os.name").startsWith("Windows")) {
            commandLine.addArgument("convert");
        }
        String[] args = new String[]{
                "-density", Integer.toString(config.getDensity()),
                "-depth ", Integer.toString(config.getDepth()),
                "-colorspace", config.getColorspace(),
                "-filter", config.getFilter(),
                "-resize", config.getResize() + "%",
                "-rotate", angle,
                sourceFile.toAbsolutePath().toString(),
                targFile.toAbsolutePath().toString()
        };
        commandLine.addArguments(args, true);
        DefaultExecutor executor = new DefaultExecutor();
        try {
            executor.execute(commandLine);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("ImageMagick failed (commandline: "+commandLine+")", e);
        }
        metadata.add(TesseractOCRParser.IMAGE_MAGICK, "true");
    }

    private String getAngle(Path sourceFile, Metadata metadata, TesseractOCRConfig config) throws IOException {
        String angle = "0";
        // fetch rotation script from resources
        TemporaryResources tmp = new TemporaryResources();
        File rotationScript = tmp.createTemporaryFile();
        try {
            try (InputStream in = getClass().getResourceAsStream("rotation.py")) {
                Files.copy(in, rotationScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }


            DefaultExecutor executor = new DefaultExecutor();
            // determine the angle of rotation required to make the text horizontal
            if (config.getApplyRotation() && hasPython(config)) {
                CommandLine commandLine = new CommandLine(getPythonPath(config));
                String[] args = {"-W",
                        "ignore",
                        rotationScript.getAbsolutePath(),
                        "-f",
                        sourceFile.toString()};
                commandLine.addArguments(args, true);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
                executor.setStreamHandler(streamHandler);
                String tmpAngle = "";
                try {
                    executor.execute(commandLine);
                    tmpAngle = outputStream.toString("UTF-8").trim();
                    //verify that you've gotten a numeric value out
                    Double.parseDouble(tmpAngle);
                    metadata.add(TesseractOCRParser.IMAGE_ROTATION, tmpAngle);
                    angle = tmpAngle;
                } catch (SecurityException e) {
                    throw e;
                } catch (Exception e) {
                    LOG.warn("rotation.py failed (commandline: " + commandLine + ") tmpAngle: " + tmpAngle, e);
                }
            }
        } finally {
            tmp.close();
        }
        return angle;
    }

    public static String getImageMagickProg() {
        return System.getProperty("os.name").startsWith("Windows") ?
                "magick" : "convert";
    }

    public static String getPythonProg() {
        return "python3";
    }

}
