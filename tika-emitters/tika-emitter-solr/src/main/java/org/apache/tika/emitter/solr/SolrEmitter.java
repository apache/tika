package org.apache.tika.emitter.solr;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.emitter.Emitter;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class SolrEmitter implements Emitter, Initializable {

    private String name = "solr";
    boolean collapseEmbeddedFiles = false;
    private String url;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void emit(List<Metadata> metadataList) throws IOException,
            TikaException {

    }

    /**
     * If set to true, this concatenates text from all embedded files
     * with the primary document's text but throws out the metadata
     * from the embedded files.
     *
     * If set to false (default), the SolrEmitter will emit attachments
     * as "children" of the parent.
     *
     * @param collapseEmbeddedFiles
     */
    @Field
    public void setCollapseEmbeddedFiles(boolean collapseEmbeddedFiles) {
        this.collapseEmbeddedFiles = collapseEmbeddedFiles;
    }

    @Field
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Specify the url for Solr
     * @param url
     */
    @Field
    public void setSolrUrl(String url) {
        this.url = url;
    }

    //TODO: add username/password for authentication?

    /**
     * Specify the field in the first Metadata that should be
     * used as the id field for the document.
     *
     * @param idField
     */
    @Field
    public void setIdField(String idField) {

    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //TODO: build the client here

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {

    }
}
