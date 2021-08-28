package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/// <summary>
/// Object Group Declarations
/// </summary>
public class ObjectGroupDeclarations extends StreamObject {
    /// <summary>
    /// Initializes a new instance of the ObjectGroupDeclarations class.
    /// </summary>
    public ObjectGroupDeclarations() {
        super(StreamObjectTypeHeaderStart.ObjectGroupDeclarations);
        this.ObjectDeclarationList = new ArrayList<>();
        this.ObjectGroupObjectBLOBDataDeclarationList = new ArrayList<>();
    }

    /// <summary>
    /// Gets or sets a list of declarations that specifies the object.
    /// </summary>
    public List<ObjectGroupObjectDeclare> ObjectDeclarationList;

    /// <summary>
    /// Gets or sets a list of object data BLOB declarations that specifies the object.
    /// </summary>
    public List<ObjectGroupObjectBLOBDataDeclaration> ObjectGroupObjectBLOBDataDeclarationList;

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        if (lengthOfItems != 0) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "ObjectGroupDeclarations",
                    "Stream object over-parse error", null);
        }

        AtomicInteger index = new AtomicInteger(currentIndex.get());
        int headerLength = 0;
        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        this.ObjectDeclarationList = new ArrayList<>();
        this.ObjectGroupObjectBLOBDataDeclarationList = new ArrayList<>();
        while ((headerLength = StreamObjectHeaderStart.TryParse(byteArray, index.get(), header)) != 0) {
            if (header.get().type == StreamObjectTypeHeaderStart.ObjectGroupObjectDeclare) {
                index.addAndGet(headerLength);
                this.ObjectDeclarationList.add(
                        (ObjectGroupObjectDeclare) StreamObject.ParseStreamObject(header.get(), byteArray, index));
            } else if (header.get().type == StreamObjectTypeHeaderStart.ObjectGroupObjectBLOBDataDeclaration) {
                index.addAndGet(headerLength);
                this.ObjectGroupObjectBLOBDataDeclarationList.add(
                        (ObjectGroupObjectBLOBDataDeclaration) StreamObject.ParseStreamObject(header.get(), byteArray,
                                index));
            } else {
                throw new StreamObjectParseErrorException(index.get(), "ObjectGroupDeclarations",
                        "Failed to parse ObjectGroupDeclarations, expect the inner object type either ObjectGroupObjectDeclare or ObjectGroupObjectBLOBDataDeclaration, but actual type value is " +
                                header.get().type, null);
            }
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List
    /// </summary>
    /// <param name="byteList">The Byte list</param>
    /// <returns>A constant value 0</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        if (this.ObjectDeclarationList != null) {
            for (ObjectGroupObjectDeclare objectGroupObjectDeclare : this.ObjectDeclarationList) {
                byteList.addAll(objectGroupObjectDeclare.SerializeToByteList());
            }
        }

        if (this.ObjectGroupObjectBLOBDataDeclarationList != null) {
            for (ObjectGroupObjectBLOBDataDeclaration objectGroupObjectBLOBDataDeclaration : this.ObjectGroupObjectBLOBDataDeclarationList) {
                byteList.addAll(objectGroupObjectBLOBDataDeclaration.SerializeToByteList());
            }
        }

        return 0;
    }
}