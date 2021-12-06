package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

/// <summary>
    /// This class is used to represent the file data.
    /// </summary>
    public class FileDataObject
    {
        /// <summary>
        /// Gets or sets the value of Object Data BLOB Declaration.
        /// </summary>
        public ObjectGroupObjectBLOBDataDeclaration ObjectDataBLOBDeclaration;
        /// <summary>
        /// Gets or sets the value of Object Data BLOB Reference.
        /// </summary>
        public ObjectGroupObjectDataBLOBReference ObjectDataBLOBReference;

        /// <summary>
        /// Gets or sets the data of file data object.
        /// </summary>
        public DataElement ObjectDataBLOBDataElement;
    }