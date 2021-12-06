package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MSONESTOREParser
    {
        // The DataElements of Storage Index
        private List<DataElement> storageIndexDataElements;
        // The DataElements of Storage Manifest
        private List<DataElement> storageManifestDataElements;
        // The DataElements of Cell Manifest
        private List<DataElement> cellManifestDataElements;
        // The DataElements of Revision Manifest
        private List<DataElement> revisionManifestDataElements;
        // The DataElements of Object Group Data
        private List<DataElement> objectGroupDataElements;
        // The DataElements of Object BLOB
        private List<DataElement> objectBlOBElements;

        private Set<CellID> storageIndexHashTab = new HashSet<>();
        public MSOneStorePackage Parse(DataElementPackage dataElementPackage)
        {
            MSOneStorePackage msOneStorePackage = new MSOneStorePackage();

            storageIndexDataElements = dataElementPackage.DataElements.stream().filter(d -> d.dataElementType == DataElementType.StorageIndexDataElementData).collect(
                    Collectors.toList());
            storageManifestDataElements = dataElementPackage.DataElements.stream().filter(d -> d.dataElementType == DataElementType.StorageManifestDataElementData).collect(
                    Collectors.toList());
            cellManifestDataElements = dataElementPackage.DataElements.stream().filter(d -> d.dataElementType == DataElementType.CellManifestDataElementData).collect(
                    Collectors.toList());
            revisionManifestDataElements = dataElementPackage.DataElements.stream().filter(d -> d.dataElementType == DataElementType.RevisionManifestDataElementData).collect(
                    Collectors.toList());
            objectGroupDataElements = dataElementPackage.DataElements.stream().filter(d -> d.dataElementType == DataElementType.ObjectGroupDataElementData).collect(
                    Collectors.toList());
            objectBlOBElements = dataElementPackage.DataElements.stream().filter(d -> d.dataElementType == DataElementType.ObjectDataBLOBDataElementData).collect(
                    Collectors.toList());

            msOneStorePackage.StorageIndex = (StorageIndexDataElementData) storageIndexDataElements.get(0).data;
            msOneStorePackage.StorageManifest = (StorageManifestDataElementData) storageManifestDataElements.get(0).data;

            // Parse Header Cell
            CellID headerCellID= msOneStorePackage.StorageManifest.StorageManifestRootDeclareList.get(0).cellID;
            StorageIndexCellMapping headerCellStorageIndexCellMapping = msOneStorePackage.FindStorageIndexCellMapping(headerCellID);
            storageIndexHashTab.add(headerCellID);

            if (headerCellStorageIndexCellMapping != null)
            {
                msOneStorePackage.HeaderCellCellManifest = this.FindCellManifest(headerCellStorageIndexCellMapping.CellMappingExGuid);
                StorageIndexRevisionMapping headerCellRevisionManifestMapping =
                    msOneStorePackage.FindStorageIndexRevisionMapping(msOneStorePackage.HeaderCellCellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid);
                msOneStorePackage.HeaderCellRevisionManifest = this.FindRevisionManifestDataElement(headerCellRevisionManifestMapping.RevisionMappingExGuid);
                msOneStorePackage.headerCell = this.ParseHeaderCell(msOneStorePackage.HeaderCellRevisionManifest);
            
                // Parse Data root
                CellID dataRootCellID = msOneStorePackage.StorageManifest.StorageManifestRootDeclareList.get(1).cellID;
                storageIndexHashTab.add(dataRootCellID);
                msOneStorePackage.DataRoot = this.ParseObjectGroup(dataRootCellID, msOneStorePackage);
                // Parse other data
                for (StorageIndexCellMapping storageIndexCellMapping : msOneStorePackage.StorageIndex.StorageIndexCellMappingList)
                {
                    if (!storageIndexHashTab.contains(storageIndexCellMapping.CellID))
                    {
                        msOneStorePackage.OtherFileNodeList.addAll(this.ParseObjectGroup(storageIndexCellMapping.CellID,msOneStorePackage));
                        storageIndexHashTab.add(storageIndexCellMapping.CellID);
                    }
                }
            }
            return msOneStorePackage;
        }

        /// <summary>
        /// Find the CellManifestDataElementData
        /// </summary>
        /// <param name="cellMappingExtendedGUID">The ExGuid of Cell Mapping Extended GUID.</param>
        /// <returns>Return the CellManifestDataElementData instance.</returns>
        private CellManifestDataElementData FindCellManifest(ExGuid cellMappingExtendedGUID)
        {
            return (CellManifestDataElementData)this.cellManifestDataElements.stream()
                    .filter(d -> d.dataElementExGuid.equals(cellMappingExtendedGUID))
                    .findFirst().orElse(new DataElement()).data;
        }
        /// <summary>
        /// Find the Revision Manifest from Data Elements.
        /// </summary>
        /// <param name="revisionMappingExtendedGUID">The Revision Mapping Extended GUID.</param>
        /// <returns>Returns the instance of RevisionManifestDataElementData</returns>
        private RevisionManifestDataElementData FindRevisionManifestDataElement(ExGuid revisionMappingExtendedGUID)
        {
            return (RevisionManifestDataElementData)this.revisionManifestDataElements.stream()
                    .filter(d -> d.dataElementExGuid.equals(revisionMappingExtendedGUID))
                    .findFirst().orElse(new DataElement()).data;
        }

        private HeaderCell ParseHeaderCell(RevisionManifestDataElementData headerCellRevisionManifest)
        {
            ExGuid rootObjectId = headerCellRevisionManifest.RevisionManifestObjectGroupReferencesList.get(0).ObjectGroupExtendedGUID;

            DataElement element = this.objectGroupDataElements.stream()
                    .filter(d -> d.dataElementExGuid.equals(rootObjectId))
                    .findFirst().orElse(new DataElement());

            return HeaderCell.CreateInstance((ObjectGroupDataElementData)element.data);
        }

        private List<RevisionStoreObjectGroup> ParseObjectGroup(CellID objectGroupCellID, MSOneStorePackage msOneStorePackage)
        {
            StorageIndexCellMapping storageIndexCellMapping = msOneStorePackage.FindStorageIndexCellMapping(objectGroupCellID);
            CellManifestDataElementData cellManifest = this.FindCellManifest(storageIndexCellMapping.CellMappingExGuid);
            List<RevisionStoreObjectGroup> objectGroups = new ArrayList<>();
            msOneStorePackage.CellManifests.add(cellManifest);
            StorageIndexRevisionMapping revisionMapping =
                msOneStorePackage.FindStorageIndexRevisionMapping(cellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid);
            RevisionManifestDataElementData revisionManifest =
                FindRevisionManifestDataElement(revisionMapping.RevisionMappingExGuid);
            msOneStorePackage.RevisionManifests.add(revisionManifest);
            RevisionManifestRootDeclare encryptionKeyRoot =
                    revisionManifest.RevisionManifestRootDeclareList.stream().filter(r -> r.RootExGuid.equals(new ExGuid(3, UUID.fromString("4A3717F8-1C14-49E7-9526-81D942DE1741")))).findFirst().orElse(null);
            boolean isEncryption = encryptionKeyRoot != null;
            for (RevisionManifestObjectGroupReferences objRef : revisionManifest.RevisionManifestObjectGroupReferencesList) {
                ObjectGroupDataElementData dataObject = (ObjectGroupDataElementData)objectGroupDataElements.stream().filter(d -> d.dataElementExGuid.equals(
                                         objRef.ObjectGroupExtendedGUID)).findFirst().get().data;

                RevisionStoreObjectGroup objectGroup = RevisionStoreObjectGroup.CreateInstance(objRef.ObjectGroupExtendedGUID, dataObject, isEncryption);
                objectGroups.add(objectGroup);
            }

            return objectGroups;
        }
    }