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

/**
 * Abstract base class for new detectors. This class has a convenience method for
 * creating a DetectorProxy
 *
 * @since Apache Tika 2.0
 */
public abstract class AbstractDetector implements Detector {
    
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = -5869078281784941763L;

    /**
     * Convenience method for creating DetectorProxy instances
     * with the current class' ClassLoader
     * 
     * @param detectorClassName
     * @return
     */
    public Detector createDetectorProxy(String detectorClassName){
        return new DetectorProxy(detectorClassName, getClass().getClassLoader());
    }

}
