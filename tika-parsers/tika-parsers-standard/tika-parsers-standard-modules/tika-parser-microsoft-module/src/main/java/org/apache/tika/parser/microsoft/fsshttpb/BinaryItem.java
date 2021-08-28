package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BinaryItem extends BasicObject
    {
        /// <summary>
        /// A byte stream that specifies the data for the item.
        /// </summary>
        private java.util.List<Byte> content = null;

        /// <summary>
        /// Initializes a new instance of the BinaryItem class.
        /// </summary>
        public BinaryItem()
        {
            this.Length = new Compact64bitInt();
            this.content = new ArrayList<>();
        }

        /// <summary>
        /// Initializes a new instance of the BinaryItem class with the specified content.
        /// </summary>
        /// <param name="content">Specify the binary content.</param>
        public BinaryItem(Collection<Byte> content)
        {
            this.content = new ArrayList<>();
            this.content.addAll(content);
            this.Length.setDecodedValue(this.Content.size());
        }

        /// <summary>
        /// Gets or sets a compact unsigned 64-bit integer that specifies the count of bytes of Content of the item. 
        /// </summary>
        public Compact64bitInt Length;
       
        /// <summary>
        /// Gets or sets a byte stream that specifies the data for the item.
        /// </summary>
        public List<Byte> Content;

        /// <summary>
        /// This method is used to convert the element of BinaryItem basic object into a byte List.
        /// </summary>
        /// <returns>Return the byte list which store the byte information of BinaryItem.</returns>
        @Override
        public List<Byte> SerializeToByteList()
        {
            this.Length.setDecodedValue(this.content.size());

            List<Byte> result = new ArrayList<>();
            result.addAll(this.Length.SerializeToByteList());
            result.addAll(this.content);

            return result;
        }

        /// <summary>
        /// This method is used to de-serialize the BinaryItem basic object from the specified byte array and start index.
        /// </summary>
        /// <param name="byteArray">Specify the byte array.</param>
        /// <param name="startIndex">Specify the start index from the byte array.</param>
        /// <returns>Return the length in byte of the BinaryItem basic object.</returns>
        @Override
        protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex)
        {
            AtomicInteger index = new AtomicInteger(startIndex);

            this.Length = BasicObject.parse(byteArray, index, Compact64bitInt.class);
            
            this.Content.clear();
            for (long i = 0; i < this.Length.getDecodedValue(); i++)
            {
                this.Content.add(byteArray[index.getAndIncrement()]);
            }

            return index.get() - startIndex;
        }
    }