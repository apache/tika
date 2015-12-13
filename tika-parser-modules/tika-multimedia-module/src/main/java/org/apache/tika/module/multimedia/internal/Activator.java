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
package org.apache.tika.module.multimedia.internal;

import org.apache.tika.osgi.TikaAbstractBundleActivator;
import org.apache.tika.parser.audio.AudioParser;
import org.apache.tika.parser.audio.MidiParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.jpeg.JpegParser;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.parser.mp4.MP4Parser;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.video.FLVParser;
import org.osgi.framework.BundleContext;

public class Activator extends TikaAbstractBundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {

        registerTikaService(context, new JpegParser(), null);
        registerTikaService(context, new ImageParser(), null);
        registerTikaService(context, new AudioParser(), null);
        registerTikaService(context, new MidiParser(), null);
        registerTikaService(context, new TesseractOCRParser(), null);
        registerTikaService(context, new FLVParser(), null);
        registerTikaService(context, new Mp3Parser(), null);
        registerTikaService(context, new MP4Parser(), null);

    }

    @Override
    public void stop(BundleContext context) throws Exception {

    }

}
