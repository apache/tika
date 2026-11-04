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
package org.apache.tika.parser.jupyter;

import java.util.List;
import java.util.Set;

/**
 * Picks a notebook's thumbnail the way nbsphinx does, so a notebook shows the
 * same preview here as in an nbsphinx gallery. This follows nbsphinx's
 * {@code Exporter.from_notebook_node} and {@code _extract_thumbnail}
 * (src/nbsphinx/__init__.py, spatialaudio/nbsphinx, as of commit 0d01427):
 * <ol>
 *   <li>A code cell marked with {@code nbsphinx-thumbnail}, as cell metadata (an
 *   object with an optional zero-based {@code output-index} and a {@code tooltip})
 *   or as a tag, names the thumbnail: the output at {@code output-index}, else the
 *   cell's last output. The metadata key is read before the tag.</li>
 *   <li>A marker that cannot be honoured makes the thumbnail broken, and nbsphinx
 *   then does not fall back to the implicit choice: other keys in the metadata
 *   object, a marker on a cell that is not a code cell, a second marked cell, a
 *   cell without outputs, an index outside the outputs, an output that is not
 *   display_data or execute_result, an output without an image. Here a broken
 *   marker means no thumbnail and a warning.</li>
 *   <li>Without a marker, the last image in the notebook: code cells from the
 *   last, their outputs from the last, the first output that holds an image.</li>
 * </ol>
 * Within an output, the image is SVG, else PNG, else JPEG, the order of
 * nbsphinx's {@code DISPLAY_DATA_PRIORITY_HTML} restricted to its
 * {@code THUMBNAIL_MIME_TYPES}.
 *
 * @see <a href="https://nbsphinx.readthedocs.io/en/latest/subdir/gallery.html">nbsphinx
 * thumbnail galleries</a>
 */
final class NbsphinxThumbnail {

    static final List<String> IMAGE_PRIORITY = List.of("image/svg+xml", "image/png", "image/jpeg");

    private static final Set<String> IMAGE_OUTPUT_TYPES = Set.of("display_data", "execute_result");

    /** The chosen image, or the reason a marker could not be honoured; both null for none. */
    record Choice(int cell, int output, String mimeType, String problem) {

        static final Choice NONE = new Choice(-1, -1, null, null);

        boolean found() {
            return mimeType != null;
        }

        boolean isAt(int cellIndex, int outputIndex) {
            return found() && cell == cellIndex && output == outputIndex;
        }
    }

    private NbsphinxThumbnail() {
    }

    static Choice choose(List<Notebook.Cell> cells) {
        Choice explicit = null;
        for (int i = 0; i < cells.size(); i++) {
            Notebook.Cell cell = cells.get(i);
            Notebook.Marker marker = cell.marker();
            if (marker == null) {
                continue;
            }
            if (marker.invalid()) {
                return broken(i, "invalid key(s), only output-index and tooltip are allowed");
            }
            if (!"code".equals(cell.type())) {
                return broken(i, "only allowed in code cells, not \"" + cell.type() + "\"");
            }
            if (explicit != null) {
                return broken(i, "only allowed once per notebook");
            }
            int outputIndex = marker.outputIndex() == null
                    ? cell.outputs().size() - 1 : marker.outputIndex();
            if (cell.outputs().isEmpty()) {
                return broken(i, "no outputs");
            }
            if (outputIndex < 0 || outputIndex >= cell.outputs().size()) {
                return broken(i, "invalid output-index " + outputIndex);
            }
            Notebook.Output output = cell.outputs().get(outputIndex);
            if (!IMAGE_OUTPUT_TYPES.contains(output.type())) {
                return broken(i, "unsupported output type \"" + output.type() + "\" in output "
                        + outputIndex);
            }
            String mimeType = imageType(output);
            if (mimeType == null) {
                return broken(i, "no image in output " + outputIndex);
            }
            explicit = new Choice(i, outputIndex, mimeType, null);
        }
        if (explicit != null) {
            return explicit;
        }
        for (int i = cells.size() - 1; i >= 0; i--) {
            Notebook.Cell cell = cells.get(i);
            if (!"code".equals(cell.type())) {
                continue;
            }
            for (int j = cell.outputs().size() - 1; j >= 0; j--) {
                Notebook.Output output = cell.outputs().get(j);
                String mimeType = IMAGE_OUTPUT_TYPES.contains(output.type()) ? imageType(output)
                        : null;
                if (mimeType != null) {
                    return new Choice(i, j, mimeType, null);
                }
            }
        }
        return Choice.NONE;
    }

    static String imageType(Notebook.Output output) {
        for (String mimeType : IMAGE_PRIORITY) {
            if (output.data().get(mimeType) != null) {
                return mimeType;
            }
        }
        return null;
    }

    private static Choice broken(int cell, String reason) {
        return new Choice(-1, -1, null,
                "\"" + Notebook.THUMBNAIL_MARKER + "\" in cell " + cell + ": " + reason);
    }
}
