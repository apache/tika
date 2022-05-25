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
package org.apache.tika.renderer;

/**
 * Empty interface for requests to a renderer. Different
 * file formats and different use cases will have different types of requests.
 * For page based, it could be a page range (render the full pages from 2 to 5);
 * or it could be a single page with an x-y bounding box.  For video files,
 * it could be a temporal offset or a temporal offset with an x-y bounding box.
 */
public interface RenderRequest {
}
