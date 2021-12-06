package org.apache.tika.parser.microsoft.fsshttpb;

/// <summary>
    /// The class is used to represent the revision store object.
    /// </summary>
    public class RevisionStoreObject
    {
        /// <summary>
        ///  Initialize the class.
        /// </summary>
        public RevisionStoreObject()
        {
           
        }
        /// <summary>
        /// Gets or sets the object identifier.
        /// </summary>
        public ExGuid ObjectID;
        /// <summary>
        /// Gets or sets the object group identifier.
        /// </summary>
        public ExGuid ObjectGroupID;
        /// <summary>
        /// Gets or sets the value of Object Declaration.
        /// </summary>
        public JCIDObject JCID;
        /// <summary>
        /// Gets or sets the value of PropertySet.
        /// </summary>
        public PropertySetObject PropertySet;
        /// <summary>
        /// Gets or sets the value of FileDataObject.
        /// </summary>
        public FileDataObject FileDataObject;
        /// <summary>
        /// Gets or sets the identifiers of the referenced objects in the revision store.
        /// </summary>
        public ExGUIDArray ReferencedObjectID;
        /// <summary>
        /// Gets or sets the identifiers of the referenced object spaces in the revision store.
        /// </summary>
        public CellIDArray ReferencedObjectSpacesID;
    }