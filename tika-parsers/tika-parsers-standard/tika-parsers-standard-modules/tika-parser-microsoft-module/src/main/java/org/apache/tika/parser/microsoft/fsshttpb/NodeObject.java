package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

public abstract class NodeObject extends StreamObject {
    /**
     * Initializes a new instance of the NodeObject class.
     *
     * @param headerType Specify the node object header type.
     */
    protected NodeObject(StreamObjectTypeHeaderStart headerType) {
        super(headerType);
    }

    /**
     * Gets or sets the extended GUID of this node object.
     */
    public ExGuid ExGuid;

    /**
     * Gets or sets the intermediate node object list.
     */
    public List<LeafNodeObject> IntermediateNodeObjectList;

    /**
     * Gets or sets the signature.
     */
    public SignatureObject Signature;

    /**
     * Gets or sets the data size.
     */
    public DataSizeObject DataSize;

    /**
     * Get all the content which is represented by the node object.
     *
     * @return Return the byte list of node object content.
     */
    public abstract List<Byte> GetContent();
}