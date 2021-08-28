package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ExGUIDArray extends BasicObject {
    /// <summary>
    /// Gets or sets an extended GUID array that specifies an array of items.
    /// </summary>
    private List<ExGuid> content = null;

    /// <summary>
    /// Initializes a new instance of the ExGUIDArray class with specified value.
    /// </summary>
    /// <param name="content">Specify the list of ExGuid contents.</param>
    public ExGUIDArray(List<ExGuid> content) {
        super();
        this.content = new ArrayList<>();
        if (content != null) {
            for (ExGuid extendGuid : content) {
                this.content.add(new ExGuid(extendGuid));
            }
        }

        this.Count.setDecodedValue(this.Content.size());
    }

    /// <summary>
    /// Initializes a new instance of the ExGUIDArray class, this is copy constructor.
    /// </summary>
    /// <param name="extendGuidArray">Specify the ExGUIDArray where copies from.</param>
    public ExGUIDArray(ExGUIDArray extendGuidArray) {
        this(extendGuidArray.Content);
    }

    /// <summary>
    /// Initializes a new instance of the ExGUIDArray class, this is the default constructor.
    /// </summary>
    public ExGUIDArray() {
        this.Count = new Compact64bitInt();
        this.content = new ArrayList<>();
    }

    /// <summary>
    /// Gets or sets a compact unsigned 64-bit integer that specifies the count extended GUIDs in the array.
    /// </summary>
    public Compact64bitInt Count;

    /// <summary>
    /// Gets or sets an extended GUID array
    /// </summary>
    public List<ExGuid> Content;

    public java.util.List<ExGuid> getContent() {
        return Content;
    }

    public void setContent(java.util.List<ExGuid> content) {
        this.content = content;
        this.Count.setDecodedValue(this.Content.size());
    }

    /// <summary>
    /// This method is used to convert the element of ExGUIDArray basic object into a byte List.
    /// </summary>
    /// <returns>Return the byte list which store the byte information of ExGUIDArray.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        this.Count.setDecodedValue(this.content.size());

        List<Byte> result = new ArrayList<Byte>();
        result.addAll(this.Count.SerializeToByteList());
        for (ExGuid extendGuid : this.content) {
            result.addAll(extendGuid.SerializeToByteList());
        }

        return result;
    }

    /// <summary>
    /// This method is used to deserialize the ExGUIDArray basic object from the specified byte array and start index.
    /// </summary>
    /// <param name="byteArray">Specify the byte array.</param>
    /// <param name="startIndex">Specify the start index from the byte array.</param>
    /// <returns>Return the length in byte of the ExGUIDArray basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) // return the length consumed
    {
        AtomicInteger index = new AtomicInteger(startIndex);
        this.Count = BasicObject.parse(byteArray, index, Compact64bitInt.class);

        this.Content.clear();
        for (int i = 0; i < this.Count.getDecodedValue(); i++) {
            ExGuid temp = BasicObject.parse(byteArray, index, ExGuid.class);
            this.Content.add(temp);
        }

        return index.get() - startIndex;
    }
}