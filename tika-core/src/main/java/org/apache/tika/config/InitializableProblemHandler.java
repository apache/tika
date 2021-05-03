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


import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;


/**
 * This is to be used to handle potential recoverable problems that
 * might arise during initialization.
 */
public interface InitializableProblemHandler {


    /**
     * Strategy that simply ignores all problems.
     */
    InitializableProblemHandler IGNORE = new InitializableProblemHandler() {
        public void handleInitializableProblem(String className, String message) {
        }

        @Override
        public String toString() {
            return "IGNORE";
        }
    };
    /**
     * Strategy that logs warnings of all problems using a {@link org.slf4j.Logger}
     * created using the given class name.
     */
    InitializableProblemHandler INFO = new InitializableProblemHandler() {
        public void handleInitializableProblem(String classname, String message) {
            LoggerFactory.getLogger(classname).info(message);
        }

        @Override
        public String toString() {
            return "INFO";
        }
    };
    /**
     * Strategy that logs warnings of all problems using a {@link org.slf4j.Logger}
     * created using the given class name.
     */
    InitializableProblemHandler WARN = new InitializableProblemHandler() {
        public void handleInitializableProblem(String classname, String message) {
            LoggerFactory.getLogger(classname).warn(message);
        }

        @Override
        public String toString() {
            return "WARN";
        }
    };
    InitializableProblemHandler THROW = new InitializableProblemHandler() {
        public void handleInitializableProblem(String classname, String message)
                throws TikaConfigException {
            throw new TikaConfigException(message);
        }

        @Override
        public String toString() {
            return "THROW";
        }
    };
    InitializableProblemHandler DEFAULT = WARN;

    void handleInitializableProblem(String className, String message) throws TikaConfigException;

}
