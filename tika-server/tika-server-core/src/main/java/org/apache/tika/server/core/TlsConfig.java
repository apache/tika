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
package org.apache.tika.server.core;

import java.util.Map;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

public class TlsConfig implements Initializable {

    private boolean active = false;
    //TODO make this configurable
    private final boolean passwordsAESEncrypted = false;
    private String keyStoreType = null;
    private String keyStorePassword = null;
    private String keyStoreFile = null;
    private String trustStoreType = null;
    private String trustStorePassword = null;
    private String trustStoreFile = null;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPasswordsAESEncrypted() {
        return passwordsAESEncrypted;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (active) {
            if (StringUtils.isBlank(keyStoreType)) {
                throw new TikaConfigException("must initialize keyStoreType");
            } else if (StringUtils.isBlank(keyStoreFile)) {
                throw new TikaConfigException("must initialize keyStoreFile");
            } else if (StringUtils.isBlank(keyStorePassword)) {
                throw new TikaConfigException("must initialize keyStorePassword");
            }
            if (hasTrustStore()) {
                if (StringUtils.isBlank(trustStoreType)) {
                    throw new TikaConfigException("must initialize trustStoreType " +
                            "if there's any trustStore info");
                } else if (StringUtils.isBlank(trustStoreFile)) {
                    throw new TikaConfigException("must initialize trustStoreFile " +
                            "if there's any trustStore info");
                } else if (StringUtils.isBlank(trustStorePassword)) {
                    throw new TikaConfigException("must initialize trustStorePassword " +
                            "if there's any trustStore info");
                }
            }
        }
    }

    @Override
    public String toString() {
        return "TlsConfig{" + "active=" + active + ", passwordsAESEncrypted=" +
                passwordsAESEncrypted + ", keyStoreType='" + keyStoreType + '\'' +
                ", keyStorePassword='" + keyStorePassword + '\'' + ", keyStoreFile='" +
                keyStoreFile + '\'' + ", trustStoreType='" + trustStoreType + '\'' +
                ", trustStorePassword='" + trustStorePassword + '\'' + ", trustStoreFile='" +
                trustStoreFile + '\'' + '}';
    }

    public boolean hasTrustStore() {
        return ! StringUtils.isBlank(trustStoreType) &&
                ! StringUtils.isBlank(trustStorePassword) &&
                ! StringUtils.isBlank(trustStoreFile);
    }
}
