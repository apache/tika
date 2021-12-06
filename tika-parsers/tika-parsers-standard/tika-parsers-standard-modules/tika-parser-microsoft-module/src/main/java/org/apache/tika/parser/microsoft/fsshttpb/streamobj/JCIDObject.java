package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.JCID;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

/// <summary>
    /// This class is used to represent the JCID object.
    /// </summary>
    public class JCIDObject
    {
        /// <summary>
        /// Construct the JCIDObject instance.
        /// </summary>
        /// <param name="objectDeclaration">The Object Declaration structure.</param>
        /// <param name="objectData">The Object Data structure.</param>
        public JCIDObject(ObjectGroupObjectDeclare objectDeclaration, ObjectGroupObjectData objectData)
        {
            this.ObjectDeclaration = objectDeclaration;
            this.JCID = new JCID();
            this.JCID.DoDeserializeFromByteArray(ByteUtil.toByteArray(objectData.Data.Content), 0);
        }
        /// <summary>
        /// Gets or sets the value of Object Declaration.
        /// </summary>
        public ObjectGroupObjectDeclare ObjectDeclaration;
        /// <summary>
        /// Gets or sets the data of object data.
        /// </summary>
        public JCID JCID;
    }