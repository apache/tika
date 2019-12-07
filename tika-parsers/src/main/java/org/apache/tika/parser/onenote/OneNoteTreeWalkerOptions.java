package org.apache.tika.parser.onenote;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Options when walking the one note tree.
 */
public class OneNoteTreeWalkerOptions {
    private boolean crawlAllFileNodesFromRoot = true;
    private boolean onlyLatestRevision = true;
    private Set<OneNotePropertyEnum> utf16PropertiesToPrint = Sets.newHashSet(OneNotePropertyEnum.ImageFilename,
      OneNotePropertyEnum.Author,
      OneNotePropertyEnum.CachedTitleString);

    /**
     * Do this to ignore revisions and just parse all file nodes from the root recursively.
     */
    public boolean isCrawlAllFileNodesFromRoot() {
        return crawlAllFileNodesFromRoot;
    }

    /**
     * Do this to ignore revisions and just parse all file nodes from the root recursively.
     *
     * @param crawlAllFileNodesFromRoot
     * @return
     */
    public OneNoteTreeWalkerOptions setCrawlAllFileNodesFromRoot(boolean crawlAllFileNodesFromRoot) {
        this.crawlAllFileNodesFromRoot = crawlAllFileNodesFromRoot;
        return this;
    }

    /**
     * Only parse the latest revision.
     */
    public boolean isOnlyLatestRevision() {
        return onlyLatestRevision;
    }

    /**
     * Only parse the latest revision.
     *
     * @param onlyLatestRevision
     * @return Returns this, as per builder pattern.
     */
    public OneNoteTreeWalkerOptions setOnlyLatestRevision(boolean onlyLatestRevision) {
        this.onlyLatestRevision = onlyLatestRevision;
        return this;
    }

    /**
     * Print file node data in UTF-16 format when they match these props.
     */
    public Set<OneNotePropertyEnum> getUtf16PropertiesToPrint() {
        return utf16PropertiesToPrint;
    }

    /**
     * Print file node data in UTF-16 format when they match these props.
     *
     * @param utf16PropertiesToPrint The set of UTF properties you want to print UTF-16 for. Defaults are usually ok here.
     * @return Returns this, as per builder pattern.
     */
    public OneNoteTreeWalkerOptions setUtf16PropertiesToPrint(Set<OneNotePropertyEnum> utf16PropertiesToPrint) {
        this.utf16PropertiesToPrint = utf16PropertiesToPrint;
        return this;
    }
}
