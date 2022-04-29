package org.apache.tika.renderer.pdf;

import org.apache.tika.renderer.Renderer;

/**
 * stub interface for the PDFParser to use to figure out if it needs
 * to pass on the PDDocument or create a temp file to be used
 * by a file-based renderer down the road.
 */
public interface PDDocumentRenderer extends Renderer {
}
