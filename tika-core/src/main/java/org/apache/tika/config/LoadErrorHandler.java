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
package org.apache.tika.config;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interface for error handling strategies in service class loading.
 * You can implement this interface for a custom error handling mechanism,
 * or use one of the predefined strategies.
 *
 * @since Apache Tika 0.9
 */
public interface LoadErrorHandler {

    /**
     * Handles a problem encountered when trying to load the specified
     * service class. The implementation can log or otherwise process
     * the given error information. If the method returns normally, then
     * the service loader simply skips this class and continues with the
     * next one.
     *
     * @param classname name of the service class
     * @param throwable the encountered problem
     */
    void handleLoadError(String classname, Throwable throwable);

    /**
     * Strategy that simply ignores all problems.
     */
    LoadErrorHandler IGNORE = new LoadErrorHandler() {
        public void handleLoadError(String classname, Throwable throwable) {
        }
    };

    /**
     * Strategy that logs warnings of all problems using a {@link Logger}
     * created using the given class name.
     */
    LoadErrorHandler WARN = new LoadErrorHandler() {
        public void handleLoadError(String classname, Throwable throwable) {
            Logger.getLogger(classname).log(
                    Level.WARNING, "Unable to load " + classname, throwable);
        }
    };

    /**
     * Strategy that throws a {@link RuntimeException} with the given
     * throwable as the root cause, thus interrupting the entire service
     * loading operation.
     */
    LoadErrorHandler THROW = new LoadErrorHandler() {
        public void handleLoadError(String classname, Throwable throwable) {
            throw new RuntimeException("Unable to load " + classname, throwable);
        }
    };

}
