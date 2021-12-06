package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;

/// <summary>
    /// This class is used to represent a PropertySet.
    /// </summary>
    public class PropertySet implements IProperty
    {
        /// <summary>
        /// Gets or sets an unsigned integer that specifies the number of properties in this PropertySet structure.
        /// </summary>
        public int CProperties;

        /// <summary>
        /// Gets or sets the value of rgPrids.
        /// </summary>
        public PropertyID[] RgPrids;
        /// <summary>
        /// Gets or sets the value of rgData field.
        /// </summary>
        public List<IProperty> RgData;

        /// <summary>
        /// This method is used to convert the element of PropertySet into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of PropertySet.</returns>
        public List<Byte> SerializeToByteList()
        {
            List<Byte> byteList = new ArrayList<>();
            for (byte b : BitConverter.getBytes(this.CProperties)) {
                byteList.add(b);
            }

            for (PropertyID propertyId : this.RgPrids)
            {
                byteList.addAll(propertyId.SerializeToByteList());
            }

            for (IProperty property : this.RgData)
            {
                byteList.addAll(property.SerializeToByteList());
            }

            return byteList;
        }
        /// <summary>
        /// This method is used to deserialize the PropertySet from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the PropertySet.</returns>
        public int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            int index = startIndex;

            this.CProperties = BitConverter.toInt16(byteArray, startIndex);
            index += 2;
            this.RgPrids = new PropertyID[this.CProperties];
            for (int i = 0; i < this.CProperties; i++)
            {
                PropertyID propertyID = new PropertyID();
                propertyID.DoDeserializeFromByteArray(byteArray, index);
                this.RgPrids[i] = propertyID;
                index += 4;
            }
            this.RgData = new ArrayList<>();
            for (PropertyID propertyID : this.RgPrids)
            {
                IProperty property = null;
                switch (PropertyType.fromIntVal(propertyID.Type))
                {
                    case NoData:
                    case Bool:
                    case ObjectID:
                    case ContextID:
                    case ObjectSpaceID:
                        property = new NoData();
                        break;
                    case ArrayOfObjectIDs:
                    case ArrayOfObjectSpaceIDs:
                    case ArrayOfContextIDs:
                        property = new ArrayNumber();
                        break;
                    case OneByteOfData:
                        property = new OneByteOfData();
                        break;
                    case TwoBytesOfData:
                        property = new TwoBytesOfData();
                        break;
                    case FourBytesOfData:
                        property = new FourBytesOfData();
                        break;
                    case EightBytesOfData:
                        property = new EightBytesOfData();
                        break;
                    case FourBytesOfLengthFollowedByData:
                        property = new PrtFourBytesOfLengthFollowedByData();
                        break;
                    case ArrayOfPropertyValues:
                        property = new PrtArrayOfPropertyValues();
                        break;
                    case PropertySet:
                        property = new PropertySet();
                        break;
                    default:
                        break;
                }
                if (property != null)
                {
                    int len = property.DoDeserializeFromByteArray(byteArray, index);
                    this.RgData.add(property);
                    index += len;
                }
            }

            return index - startIndex;
        }
    }