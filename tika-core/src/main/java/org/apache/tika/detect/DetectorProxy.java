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
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * This detector is a proxy for another detector 
 * this allows modules to use detectors from other modules
 * as optional dependencies since not including the classes
 * simply does nothing rather than throwing a ClassNotFoundException.
 *
 * @since Apache Tika 2.0
 */
public class DetectorProxy implements Detector
{
    private static final long serialVersionUID = 4534101565629801667L;
    
    private Detector detector;
    
    public DetectorProxy(String detectorClassName, ClassLoader loader) 
    {
        this(detectorClassName, loader, Boolean.getBoolean("org.apache.tika.service.proxy.error.warn") 
                ? LoadErrorHandler.WARN:LoadErrorHandler.IGNORE);
    }
    
    public DetectorProxy(String detectorClassName, ClassLoader loader, LoadErrorHandler handler) 
    {
        try 
        {
            this.detector = (Detector)Class.forName(detectorClassName, true, loader).newInstance();
        } 
        catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) 
        {
            handler.handleLoadError(detectorClassName, e);
        }
    }

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException 
    {
        if(detector != null)
        {
            return detector.detect(input, metadata);
        }
        return null;
    }

}