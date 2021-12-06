package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.space.ObjectSpaceObjectPropSet;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

/// <summary>
    /// This class is used to represent the property set.
    /// </summary>
    public class PropertySetObject
    {
        /// <summary>
        /// Construct the PropertySetObject instance.
        /// </summary>
        /// <param name="objectDeclaration">The Object Declaration structure.</param>
        /// <param name="objectData">The Object Data structure.</param>
        public PropertySetObject(ObjectGroupObjectDeclare objectDeclaration, ObjectGroupObjectData objectData)
        {
            this.ObjectDeclaration = objectDeclaration;
            this.ObjectSpaceObjectPropSet = new ObjectSpaceObjectPropSet();
            this.ObjectSpaceObjectPropSet.DoDeserializeFromByteArray(ByteUtil.toByteArray(objectData.Data.Content), 0);
        }
        /// <summary>
        /// Gets or sets the value of Object Declaration.
        /// </summary>
        public ObjectGroupObjectDeclare ObjectDeclaration;
        /// <summary>
        /// Gets or sets the data of object data.
        /// </summary>
        public ObjectSpaceObjectPropSet ObjectSpaceObjectPropSet;
    }