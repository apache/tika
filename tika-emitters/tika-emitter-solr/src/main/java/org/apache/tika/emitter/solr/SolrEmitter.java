package org.apache.tika.emitter.solr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.tika.client.HttpClientUtil;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.emitter.Emitter;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SolrEmitter implements Emitter, Initializable {

    enum AttachmentStrategy {
        SKIP,
        CONCATENATE_CONTENT,
        PARENT_CHILD,
        //anything else?
    }
    private static final Gson GSON = new Gson();
    private static final String ATTACHMENTS = "attachments";
    private static final String UPDATE_PATH = "/update";
    private static final Logger LOG = LoggerFactory.getLogger(SolrEmitter.class);

    private String name = "solr";
    private AttachmentStrategy attachmentStrategy = AttachmentStrategy.PARENT_CHILD;
    private String url;
    private String contentField = "content";
    private String idField = "id";
    private int commitWithin = 100;

    @Override
    public void emit(String emitterName, List<Metadata> metadataList) throws IOException,
            TikaException {
        if (metadataList == null || metadataList.size() == 0) {
            LOG.warn("metadataList is null or empty");
            return;
        }
        String json = jsonify(metadataList);
        LOG.debug("emitting json:"+json);
        HttpClientUtil.postJson(url+UPDATE_PATH+"?commitWithin="+getCommitWithin(), json);
    }

    private String jsonify(List<Metadata> metadataList) {
        if (attachmentStrategy == AttachmentStrategy.SKIP) {
            return toJsonString(jsonify(metadataList.get(0)));
        } else if (attachmentStrategy == AttachmentStrategy.CONCATENATE_CONTENT) {
            //this only handles text for now, not xhtml
            StringBuilder sb = new StringBuilder();
            for (Metadata metadata : metadataList) {
                String content = metadata.get(getContentField());
                if (content != null) {
                    sb.append(content).append("\n");
                }
            }
            Metadata parent = metadataList.get(0);
            parent.set(getContentField(), sb.toString());
            return toJsonString(jsonify(parent));
        } else if (attachmentStrategy == AttachmentStrategy.PARENT_CHILD) {
            if (metadataList.size() == 1) {
                JsonObject obj = jsonify(metadataList.get(0));
                return toJsonString(obj);
            }
            JsonObject parent = jsonify(metadataList.get(0));
            JsonArray children = new JsonArray();
            for (int i = 1; i < metadataList.size(); i++) {
                Metadata m = metadataList.get(i);
                m.set(idField, UUID.randomUUID().toString());
                children.add(jsonify(m));
            }
            parent.add(ATTACHMENTS, children);
            return toJsonString(parent);
        } else {
            throw new IllegalArgumentException("I don't yet support this attachment strategy: "
                    + attachmentStrategy);
        }
    }

    private String toJsonString(JsonObject obj) {
        //wrap the document into an array
        //so that Solr correctly interprets this as
        //upload docs vs a command.
        JsonArray docs = new JsonArray();
        docs.add(obj);
        return GSON.toJson(docs);
    }

    private JsonObject jsonify(Metadata metadata) {
        JsonObject obj = new JsonObject();
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 0) {
                continue;
            } else if (vals.length == 1) {
                obj.addProperty(n, vals[0]);
            } else if (vals.length > 1) {
                JsonArray valArr = new JsonArray();
                for (int i = 0; i < vals.length; i++) {
                    valArr.add(vals[i]);
                }
                obj.add(n, valArr);
            }
        }
        return obj;
    }

    /**
     * Options: "skip", "concatenate-content", "parent-child". Default is "parent-child".
     * If set to "skip", this will index only the main file and ignore all info
     * in the attachments.  If set to "concatenate", this will concatenate the
     * content extracted from the attachments into the main document and
     * then index the main document with the concatenated content _and_ the
     * main document's metadata (metadata from attachments will be thrown away).
     * If set to "parent-child", this will index the attachments as children
     * of the parent document via Solr's parent-child relationship.
     *
     * @param attachmentStrategy
     */
    @Field
    public void setAttachmentStrategy(String attachmentStrategy) {
        if (attachmentStrategy.equals("skip")) {
            this.attachmentStrategy = AttachmentStrategy.SKIP;
        } else if (attachmentStrategy.equals("concatenate-content")) {
            this.attachmentStrategy = AttachmentStrategy.CONCATENATE_CONTENT;
        } else if (attachmentStrategy.equals("parent-child")) {
            this.attachmentStrategy = AttachmentStrategy.PARENT_CHILD;
        } else {
            throw new IllegalArgumentException("Expected 'skip', 'concatenate-content' or "+
                    "'parent-child'. I regret I do not recognize: " + attachmentStrategy);
        }
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
    public void setUrl(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length()-1);
        }
        this.url = url;
    }

    /**
     * This is the field _after_ metadata mappings have been applied
     * that contains the "content" for each metadata object.
     *
     * This is the field that is used if {@link #attachmentStrategy}
     * is {@link AttachmentStrategy#CONCATENATE_CONTENT}.
     * @param contentField
     */
    @Field
    public void setContentField(String contentField) {
        this.contentField = contentField;
    }

    public String getContentField() {
        return contentField;
    }

    @Field
    public void setCommitWithin(int commitWithin) {
        this.commitWithin = commitWithin;
    }

    public int getCommitWithin() {
        return commitWithin;
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
        this.idField = idField;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //TODO: build the client here

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {

    }

    @Override
    public Set<String> getSupported() {
        return Collections.singleton(name);
    }

}
