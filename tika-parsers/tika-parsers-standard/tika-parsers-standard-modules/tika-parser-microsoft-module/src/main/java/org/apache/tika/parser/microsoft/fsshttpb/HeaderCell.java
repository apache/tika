package org.apache.tika.parser.microsoft.fsshttpb;

public class HeaderCell
    {
        /// <summary>
        /// Gets or sets the Object Declaration of root object 
        /// </summary>
        public ObjectGroupObjectDeclare ObjectDeclaration;
        /// <summary>
        /// Gets or sets the data of Object Data in root object.
        /// </summary>
        public ObjectSpaceObjectPropSet ObjectData;
        /// <summary>
        /// Create the instacne of Header Cell.
        /// </summary>
        /// <param name="objectElement">The instance of ObjectGroupDataElementData.</param>
        /// <returns>Returns the instacne of HeaderCell.</returns>
        public static HeaderCell CreateInstance(ObjectGroupDataElementData objectElement)
        {
            HeaderCell instance = new HeaderCell();

            for (int i = 0; i < objectElement.ObjectGroupDeclarations.ObjectDeclarationList.size(); i++)
            {
                if (objectElement.ObjectGroupDeclarations.ObjectDeclarationList.get(i).ObjectPartitionID != null && objectElement.ObjectGroupDeclarations.ObjectDeclarationList.get(i).ObjectPartitionID.getDecodedValue() == 1)
                {
                    instance.ObjectDeclaration = objectElement.ObjectGroupDeclarations.ObjectDeclarationList.get(i);
                    ObjectGroupObjectData objectData = objectElement.ObjectGroupData.ObjectGroupObjectDataList.get(i);
                    instance.ObjectData = new ObjectSpaceObjectPropSet();
                    instance.ObjectData.DoDeserializeFromByteArray(ByteUtil.toByteArray(objectData.Data.Content), 0);
                    break;
                }
            }

            return instance;
        }
    }