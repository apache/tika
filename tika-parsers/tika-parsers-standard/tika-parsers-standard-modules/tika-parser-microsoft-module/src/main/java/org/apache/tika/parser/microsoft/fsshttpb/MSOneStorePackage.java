package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;

public class MSOneStorePackage {
    /// <summary>
    /// Gets or sets the Storage Index.
    /// </summary>
    public StorageIndexDataElementData StorageIndex;
    /// <summary>
    /// Gets or sets the Storage Manifest.
    /// </summary>
    public StorageManifestDataElementData StorageManifest;
    /// <summary>
    /// Gets or sets the Cell Manifest of Header Cell.
    /// </summary>
    public CellManifestDataElementData HeaderCellCellManifest;
    /// <summary>
    /// Gets or sets the Revision Manifest of Header Cell.
    /// </summary>
    public RevisionManifestDataElementData HeaderCellRevisionManifest;
    /// <summary>
    /// Gets or sets the Revision Manifests.
    /// </summary>
    public List<RevisionManifestDataElementData> RevisionManifests;
    /// <summary>
    /// Gets or sets the Cell Manifests.
    /// </summary>
    public List<CellManifestDataElementData> CellManifests;
    /// <summary>
    /// Gets or sets the Header Cell.
    /// </summary>
    public HeaderCell headerCell;
    /// <summary>
    /// Gets or sets the root objects of the revision store file.
    /// </summary>
    public List<RevisionStoreObjectGroup> DataRoot;
    /// <summary>
    /// Gets or sets the other objects of the revision store file.
    /// </summary>
    public List<RevisionStoreObjectGroup> OtherFileNodeList;

    public MSOneStorePackage() {
        this.RevisionManifests = new ArrayList<>();
        this.CellManifests = new ArrayList<>();
        this.OtherFileNodeList = new ArrayList<>();
    }

    /// <summary>
    /// This method is used to find the Storage Index Cell Mapping matches the Cell ID.
    /// </summary>
    /// <param name="cellID">Specify the Cell ID.</param>
    /// <returns>Return the specific Storage Index Cell Mapping.</returns>
    public StorageIndexCellMapping FindStorageIndexCellMapping(CellID cellID) {
        StorageIndexCellMapping storageIndexCellMapping = null;
        if (this.StorageIndex != null) {
            storageIndexCellMapping = this.StorageIndex.StorageIndexCellMappingList
                    .stream()
                    .filter(s -> s.CellID.equals(cellID)).findFirst().orElse(new StorageIndexCellMapping());
        }
        return storageIndexCellMapping;
    }

    /// <summary>
    /// This method is used to find the Storage Index Revision Mapping that matches the Revision Mapping Extended GUID.
    /// </summary>
    /// <param name="revisionExtendedGUID">Specify the Revision Mapping Extended GUID.</param>
    /// <returns>Return the instance of Storage Index Revision Mapping.</returns>
    public StorageIndexRevisionMapping FindStorageIndexRevisionMapping(ExGuid revisionExtendedGUID) {
        StorageIndexRevisionMapping instance = null;
        if(this.StorageIndex!=null) {
            instance = this.StorageIndex.StorageIndexRevisionMappingList.stream()
                            .filter(r -> r.RevisionExGuid.equals(revisionExtendedGUID))
                                    .findFirst().orElse(new StorageIndexRevisionMapping());
        }

        return instance;
    }
}
