package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

/// <summary>
    /// The class is used to represent the encryption revision store object.
    /// </summary>
    public class EncryptionObject
    {
        /// <summary>
        /// Gets or sets the value of Object Declaration.
        /// </summary>
        public ObjectGroupObjectDeclare ObjectDeclaration;
        /// <summary>
        /// Gets or sets the data of object.
        /// </summary>
        public byte[] ObjectData;
    }