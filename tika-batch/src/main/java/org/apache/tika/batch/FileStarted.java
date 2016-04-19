package org.apache.tika.batch;

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

import java.util.Date;

/**
 * Simple class to record the time when a FileResource's processing started.
 */
class FileStarted {

    private final String resourceId;
    private final long started;

    /**
     * Initializes a new FileStarted class with {@link #resourceId}
     * and sets {@link #started} as new Date().getTime().
     *
     * @param resourceId string for unique resource id
     */
    public FileStarted(String resourceId) {
        this(resourceId, new Date().getTime());
    }

    public FileStarted(String resourceId, long started) {
        this.resourceId = resourceId;
        this.started = started;
    }


    /**
     * @return id of resource
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * @return time at which processing on this file started
     */
    public long getStarted() {
        return started;
    }

    /**
     * @return elapsed milliseconds this the start of processing of this
     * file resource
     */
    public long getElapsedMillis() {
        long now = new Date().getTime();
        return now - started;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((resourceId == null) ? 0 : resourceId.hashCode());
        result = prime * result + (int) (started ^ (started >>> 32));
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
        if (!(obj instanceof FileStarted)) {
            return false;
        }
        FileStarted other = (FileStarted) obj;
        if (resourceId == null) {
            if (other.resourceId != null) {
                return false;
            }
        } else if (!resourceId.equals(other.resourceId)) {
            return false;
        }
        return started == other.started;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FileStarted [resourceId=");
        builder.append(resourceId);
        builder.append(", started=");
        builder.append(started);
        builder.append("]");
        return builder.toString();
    }


}
