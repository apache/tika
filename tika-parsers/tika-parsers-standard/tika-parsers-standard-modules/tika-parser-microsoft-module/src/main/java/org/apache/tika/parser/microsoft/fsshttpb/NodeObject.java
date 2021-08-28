package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

public abstract class NodeObject extends StreamObject
    {
        /// <summary>
        /// Initializes a new instance of the NodeObject class.
        /// </summary>
        /// <param name="headerType">Specify the node object header type.</param>
        protected NodeObject(StreamObjectTypeHeaderStart headerType)
        {
                super(headerType);
        }

        /// <summary>
        /// Gets or sets the extended GUID of this node object.
        /// </summary>
        public ExGuid ExGuid;

        /// <summary>
        /// Gets or sets the intermediate node object list.
        /// </summary>
        public List<LeafNodeObject> IntermediateNodeObjectList;

        /// <summary>
        /// Gets or sets the signature.
        /// </summary>
        public SignatureObject Signature;

        /// <summary>
        /// Gets or sets the data size.
        /// </summary>
        public DataSizeObject DataSize;

        /// <summary>
        /// Get all the content which is represented by the node object.
        /// </summary>
        /// <returns>Return the byte list of node object content.</returns>
        public abstract List<Byte> GetContent();
    }