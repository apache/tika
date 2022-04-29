package org.apache.tika.renderer;

/**
 * Use this in the ParseContext to keep track of unique ids for rendered
 * images in embedded docs. This should be used for the full parse of
 * a main document and its embedded document.
 */
public class RenderingTracker {

    private int id = 0;

    public synchronized int getNextId() {
        return ++id;
    }
}
