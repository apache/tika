/* Copyright 2015-2016 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wordperfect;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * WordPerfect file header.
 * @author Pascal Essiembre
 */
public class WP6FileHeader {

    // Normal header
    private String fileId;
    private long docAreaPointer;
    private int productType;
    private int fileType;
    private int majorVersion;
    private int minorVersion;
    private boolean encrypted;
    private int indexAreaPointer;

    // Extended header
    private long fileSize;
    
    public WP6FileHeader() {
        super();
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public long getDocAreaPointer() {
        return docAreaPointer;
    }

    public void setDocAreaPointer(long docAreaPointer) {
        this.docAreaPointer = docAreaPointer;
    }

    public int getProductType() {
        return productType;
    }

    public void setProductType(int productType) {
        this.productType = productType;
    }

    public int getFileType() {
        return fileType;
    }

    public void setFileType(int fileType) {
        this.fileType = fileType;
    }
    
    public int getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(int majorVersion) {
        this.majorVersion = majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }
    
    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public int getIndexAreaPointer() {
        return indexAreaPointer;
    }

    public void setIndexAreaPointer(int indexAreaPointer) {
        this.indexAreaPointer = indexAreaPointer;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.append("fileId", fileId);
        builder.append("docAreaPointer", docAreaPointer);
        builder.append("productType", productType);
        builder.append("fileType", fileType);
        builder.append("majorVersion", majorVersion);
        builder.append("minorVersion", minorVersion);
        builder.append("encrypted", encrypted);
        builder.append("indexAreaPointer", indexAreaPointer);
        builder.append("fileSize", fileSize);
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + (int) (docAreaPointer ^ (docAreaPointer >>> 32));
        result = prime * result + (encrypted ? 1231 : 1237);
        result = prime * result + ((fileId == null) ? 0 : fileId.hashCode());
        result = prime * result + (int) (fileSize ^ (fileSize >>> 32));
        result = prime * result + fileType;
        result = prime * result + indexAreaPointer;
        result = prime * result + majorVersion;
        result = prime * result + minorVersion;
        result = prime * result + productType;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof WP6FileHeader)) {
            return false;
        }
        WP6FileHeader other = (WP6FileHeader) obj;
        if (docAreaPointer != other.docAreaPointer) {
            return false;
        }
        if (encrypted != other.encrypted) {
            return false;
        }
        if (fileId == null) {
            if (other.fileId != null) {
                return false;
            }
        } else if (!fileId.equals(other.fileId)) {
            return false;
        }
        if (fileSize != other.fileSize) {
            return false;
        }
        if (fileType != other.fileType) {
            return false;
        }
        if (indexAreaPointer != other.indexAreaPointer) {
            return false;
        }
        if (majorVersion != other.majorVersion) {
            return false;
        }
        if (minorVersion != other.minorVersion) {
            return false;
        }
        if (productType != other.productType) {
            return false;
        }
        return true;
    }

    
}
