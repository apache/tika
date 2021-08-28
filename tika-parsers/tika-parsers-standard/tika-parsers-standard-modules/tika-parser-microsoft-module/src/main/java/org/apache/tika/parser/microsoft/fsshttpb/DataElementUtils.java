package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.tika.parser.microsoft.onenote.GUID;
import sun.security.x509.SerialNumber;

public class DataElementUtils {
    public static final UUID RootExGuid = UUID.fromString("84DEFAB9-AAA3-4A0D-A3A8-520C77AC7073");
    public static final UUID CellSecondExGuid = UUID.fromString("6F2A4665-42C8-46C7-BAB4-E28FDCE1E32B");
    public static final UUID SchemaGuid = UUID.fromString("0EB93394-571D-41E9-AAD3-880D92D31955");

    /**
     * This method is used to build a list of data elements to represent a file.
     * @param fileContent The file content in byte array form.
     * @param storageIndexExGuid  Reference set to the storage index ex guid.
     * @return List of DataElement objects that are stored in the file content.
     */
    public static List<DataElement> BuildDataElements(byte[] fileContent, AtomicReference<ExGuid> storageIndexExGuid) {
        List<ExGuid> objectDataExGuidList = new ArrayList<>();
        AtomicReference<ExGuid> rootNodeObjectExGuid = new AtomicReference<>();
        List<DataElement> dataElementList = CreateObjectGroupDataElement(fileContent, rootNodeObjectExGuid, objectDataExGuidList);

        ExGuid baseRevisionID = new ExGuid(0, GuidUtil.emptyGuid());
        Map<ExGuid, ExGuid> revisionMapping = new HashMap<>();
        AtomicReference<ExGuid> currentRevisionID = new AtomicReference<>();
        dataElementList.add(CreateRevisionManifestDataElement(rootNodeObjectExGuid.get(), baseRevisionID, objectDataExGuidList, revisionMapping, currentRevisionID));

        Map<CellID, ExGuid> cellIDMapping = new HashMap<>();
        dataElementList.add(CreateCellMainifestDataElement(currentRevisionID.get(), cellIDMapping));

        dataElementList.add(CreateStorageManifestDataElement(cellIDMapping));
        dataElementList.add(CreateStorageIndexDataElement(dataElementList.get(dataElementList.size() - 1).dataElementExGuid, cellIDMapping, revisionMapping));

        storageIndexExGuid.set(dataElementList.get(dataElementList.size() - 1).dataElementExGuid);
        return dataElementList;
    }

    /**
     * This method is used to create object group data/blob element list.
     * @param fileContent The file content in byte array format.
     * @param rootNodeExGuid Output parameter to represent the root node extended GUID.
     * @param objectDataExGuidList Input/Output parameter to represent the list of extended GUID for the data object data.
     * @return Return the list of data element which will represent the file content.
     */
    public static List<DataElement> CreateObjectGroupDataElement(byte[] fileContent, AtomicReference<ExGuid> rootNodeExGuid, List<ExGuid> objectDataExGuidList)
    {
        NodeObject rootNode = new IntermediateNodeObject.RootNodeObjectBuilder().Build(fileContent);

        // Storage the root object node ExGuid
        rootNodeExGuid.set(new ExGuid(rootNode.ExGuid));
        List<DataElement> elements = new ObjectGroupDataElementData.Builder().Build(rootNode);
        elements.stream()
                .filter(element -> element.dataElementType == DataElementType.ObjectGroupDataElementData)
                        .forEach(element -> objectDataExGuidList.add(element.dataElementExGuid));

        return elements;
    }

    /**
     * This method is used to create the revision manifest data element.
     * @param rootObjectExGuid Specify the root node object extended GUID.
     * @param baseRevisionID Specify the base revision Id.
     * @param refferenceObjectDataExGuidList Specify the reference object data extended list.
     * @param currentRevisionID Input/output parameter to represent the mapping of revision manifest.
     * @param currentRevisionID Output parameter to represent the revision GUID.
     * @return Return the revision manifest data element.
     */
    public static DataElement CreateRevisionManifestDataElement(ExGuid rootObjectExGuid,
                                                                ExGuid baseRevisionID,
                                                                List<ExGuid> refferenceObjectDataExGuidList,
                                                                Map<ExGuid, ExGuid> revisionMapping,
                                                                AtomicReference<ExGuid> currentRevisionID) {
        RevisionManifestDataElementData data = new RevisionManifestDataElementData();
        data.RevisionManifest.RevisionID = new ExGuid(1u, UUID.randomUUID());
        data.RevisionManifest.BaseRevisionID = new ExGuid(baseRevisionID);

        // Set the root object data ExGuid
        data.RevisionManifestRootDeclareList.Add(new RevisionManifestRootDeclare() { RootExGuid = new ExGuid(2u, RootExGuid), ObjectExGuid = new ExGuid(rootObjectExGuid) });

        // Set all the reference object data
        if (refferenceObjectDataExGuidList != null)
        {
            foreach (ExGuid dataGuid in refferenceObjectDataExGuidList)
            {
                data.RevisionManifestObjectGroupReferencesList.Add(new RevisionManifestObjectGroupReferences(dataGuid));
            }
        }

        DataElement dataElement = new DataElement(DataElementType.RevisionManifestDataElementData, data);
        revisionMapping.Add(data.RevisionManifest.RevisionID, dataElement.DataElementExGuid);
        currentRevisionID = data.RevisionManifest.RevisionID;
        return dataElement;
    }

    /**
     * This method is used to create the cell manifest data element.
     * @param revisionId Specify the revision GUID.
     * @param cellIDMapping Input/output parameter to represent the mapping of cell manifest.
     * @return Return the cell manifest data element.
     */
    public static DataElement CreateCellMainifestDataElement(ExGuid revisionId, Map<CellID, ExGuid> cellIDMapping)
    {
        CellManifestDataElementData data = new CellManifestDataElementData();
        data.CellManifestCurrentRevision = new CellManifestCurrentRevision() { CellManifestCurrentRevisionExGuid = new ExGuid(revisionId) };
        DataElement dataElement = new DataElement(DataElementType.CellManifestDataElementData, data);

        CellID cellID = new CellID(new ExGuid(1u, RootExGuid), new ExGuid(1u, CellSecondExGuid));
        cellIDMapping.Add(cellID, dataElement.DataElementExGuid);
        return dataElement;
    }

    /**
     * This method is used to create the storage manifest data element.
     * @param cellIDMapping Specify the mapping of cell manifest.
     * @return The storage manifest data element.
     */
    public static DataElement CreateStorageManifestDataElement(Map<CellID, ExGuid> cellIDMapping)
    {
        StorageManifestDataElementData data = new StorageManifestDataElementData();
        data.StorageManifestSchemaGUID = new StorageManifestSchemaGUID() { GUID = SchemaGuid };

        foreach (KeyValuePair<CellID, ExGuid> kv in cellIDMapping)
        {
            StorageManifestRootDeclare manifestRootDeclare = new StorageManifestRootDeclare();
            manifestRootDeclare.RootExGuid = new ExGuid(2u, RootExGuid);
            manifestRootDeclare.CellID = new CellID(kv.Key);
            data.StorageManifestRootDeclareList.Add(manifestRootDeclare);
        }

        return new DataElement(DataElementType.StorageManifestDataElementData, data);
    }

    /**
     * This method is used to create the storage index data element.
     * @param manifestExGuid Specify the storage manifest data element extended GUID.
     * @param cellIDMappings Specify the mapping of cell manifest.
     * @param revisionIDMappings Specify the mapping of revision manifest.
     * @return The storage index data element.
     */
    public static DataElement CreateStorageIndexDataElement(ExGuid manifestExGuid, Map<CellID, ExGuid> cellIDMappings, Map<ExGuid, ExGuid> revisionIDMappings)
    {
        StorageIndexDataElementData data = new StorageIndexDataElementData();

        data.StorageIndexManifestMapping = new StorageIndexManifestMapping();
        data.StorageIndexManifestMapping.ManifestMappingExGuid = new ExGuid(manifestExGuid);
        data.StorageIndexManifestMapping.ManifestMappingSerialNumber = new SerialNumber(System.UUID.randomUUID(), SequenceNumberGenerator.GetCurrentSerialNumber());

        foreach (KeyValuePair<CellID, ExGuid> kv in cellIDMappings)
        {
            StorageIndexCellMapping cellMapping = new StorageIndexCellMapping();
            cellMapping.CellID = kv.Key;
            cellMapping.CellMappingExGuid = kv.Value;
            cellMapping.CellMappingSerialNumber = new SerialNumber(System.UUID.randomUUID(), SequenceNumberGenerator.GetCurrentSerialNumber());
            data.StorageIndexCellMappingList.Add(cellMapping);
        }

        foreach (KeyValuePair<ExGuid, ExGuid> kv in revisionIDMappings)
        {
            StorageIndexRevisionMapping revisionMapping = new StorageIndexRevisionMapping();
            revisionMapping.RevisionExGuid = kv.Key;
            revisionMapping.RevisionMappingExGuid = kv.Value;
            revisionMapping.RevisionMappingSerialNumber = new SerialNumber(UUID.randomUUID(), SequenceNumberGenerator.GetCurrentSerialNumber());
            data.StorageIndexRevisionMappingList.Add(revisionMapping);
        }

        return new DataElement(DataElementType.StorageIndexDataElementData, data);
    }

    /// <summary>
    /// This method is used to get the list of object group data element from a list of data element.
    /// </summary>
    /// <param name="dataElements">Specify the data element list.</param>
    /// <param name="storageIndexExGuid">Specify the storage index extended GUID.</param>
    /// <param name="rootExGuid">Output parameter to represent the root node object.</param>
    /// <returns>Return the list of object group data elements.</returns>
    public static List<ObjectGroupDataElementData> GetDataObjectDataElementData(List<DataElement> dataElements, ExGuid storageIndexExGuid, AtomicReference<ExGuid> rootExGuid)
    {
        AtomicReference<ExGuid> manifestMappingGuid = new AtomicReference<>();
        AtomicReference<HashMap<CellID, ExGuid>> cellIDMappings = new AtomicReference<>();
        AtomicReference<HashMap<ExGuid, ExGuid>> revisionIDMappings = new AtomicReference<>();
        AnalyzeStorageIndexDataElement(dataElements, storageIndexExGuid, manifestMappingGuid, cellIDMappings, revisionIDMappings);
        StorageManifestDataElementData manifestData = GetStorageManifestDataElementData(dataElements, manifestMappingGuid);
        if (manifestData == null)
        {
            throw new InvalidOperationException("Cannot find the storage manifest data element with ExGuid " + manifestMappingGuid.GUID.ToString());
        }

        CellManifestDataElementData cellData = GetCellManifestDataElementData(dataElements, manifestData, cellIDMappings);
        RevisionManifestDataElementData revisionData = GetRevisionManifestDataElementData(dataElements, cellData, revisionIDMappings);
        return GetDataObjectDataElementData(dataElements, revisionData, out rootExGuid);
    }

    /// <summary>
    /// This method is used to try to analyze the returned whether data elements are complete.
    /// </summary>
    /// <param name="dataElements">Specify the data elements list.</param>
    /// <param name="storageIndexExGuid">Specify the storage index extended GUID.</param>
    /// <returns>If the data elements start with the specified storage index extended GUID are complete, return true. Otherwise return false.</returns>
    public static boolean TryAnalyzeWhetherFullDataElementList(List<DataElement> dataElements, ExGuid storageIndexExGuid)
    {
        ExGuid manifestMappingGuid;
        HashMap<CellID, ExGuid> cellIDMappings;
        HashMap<ExGuid, ExGuid> revisionIDMappings;
        if (!AnalyzeStorageIndexDataElement(dataElements, storageIndexExGuid, out manifestMappingGuid, out cellIDMappings, out revisionIDMappings))
        {
            return false;
        }

        if (cellIDMappings.Count == 0)
        {
            return false;
        }

        if (revisionIDMappings.Count == 0)
        {
            return false;
        }

        StorageManifestDataElementData manifestData = GetStorageManifestDataElementData(dataElements, manifestMappingGuid);
        if (manifestData == null)
        {
            return false;
        }

        foreach (StorageManifestRootDeclare kv in manifestData.StorageManifestRootDeclareList)
        {
            if (!cellIDMappings.ContainsKey(kv.CellID))
            {
                throw new InvalidOperationException(String.format("Cannot fin the Cell ID %s in the cell id mapping", kv.CellID.ToString()));
            }

            ExGuid cellMappingID = cellIDMappings[kv.CellID];
            DataElement dataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(cellMappingID));
            if (dataElement == null)
            {
                return false;
            }

            CellManifestDataElementData cellData = dataElement.GetData<CellManifestDataElementData>();
            ExGuid currentRevisionExGuid = cellData.CellManifestCurrentRevision.CellManifestCurrentRevisionExtendedGUID;
            if (!revisionIDMappings.ContainsKey(currentRevisionExGuid))
            {
                throw new InvalidOperationException(String.format("Cannot find the revision id %s in the revisionMapping", currentRevisionExGuid.ToString()));
            }

            ExGuid revisionMapping = revisionIDMappings[currentRevisionExGuid];
            dataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(revisionMapping));
            if (dataElement == null)
            {
                return false;
            }

            RevisionManifestDataElementData revisionData = dataElement.GetData<RevisionManifestDataElementData>();
            foreach (RevisionManifestObjectGroupReferences reference in revisionData.RevisionManifestObjectGroupReferencesList)
            {
                dataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(reference.ObjectGroupExtendedGUID));
                if (dataElement == null)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /// <summary>
    /// This method is used to analyze whether the data elements are confirmed to the schema defined in MS-FSSHTTPD. 
    /// </summary>
    /// <param name="dataElements">Specify the data elements list.</param>
    /// <param name="storageIndexExGuid">Specify the storage index extended GUID.</param>
    /// <returns>If the data elements confirms to the schema defined in the MS-FSSHTTPD returns true, otherwise false.</returns>
    public static boolean TryAnalyzeWhetherConfirmSchema(List<DataElement> dataElements, ExGuid storageIndexExGuid)
    {
        DataElement storageIndexDataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(storageIndexExGuid));
        if (storageIndexExGuid == null)
        {
            return false;
        }

        StorageIndexDataElementData storageIndexData = storageIndexDataElement.GetData<StorageIndexDataElementData>();
        ExGuid manifestMappingGuid = storageIndexData.StorageIndexManifestMapping.ManifestMappingExtendedGUID;

        DataElement storageManifestDataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(manifestMappingGuid));
        if (storageManifestDataElement == null)
        {
            return false;
        }

        return SchemaGuid.Equals(storageManifestDataElement.GetData<StorageManifestDataElementData>().StorageManifestSchemaGUID.GUID);
    }

    /// <summary>
    /// This method is used to analyze the storage index data element to get all the mappings. 
    /// </summary>
    /// <param name="dataElements">Specify the data element list.</param>
    /// <param name="storageIndexExGuid">Specify the storage index extended GUID.</param>
    /// <param name="manifestMappingGuid">Output parameter to represent the storage manifest mapping GUID.</param>
    /// <param name="cellIDMappings">Output parameter to represent the mapping of cell id.</param>
    /// <param name="revisionIDMappings">Output parameter to represent the revision id.</param>
    /// <returns>Return true if analyze the storage index succeeds, otherwise return false.</returns>
    public static boolean AnalyzeStorageIndexDataElement(
            List<DataElement> dataElements,
            ExGuid storageIndexExGuid,
            AtomicReference<ExGuid> manifestMappingGuid,
            AtomicReference<HashMap<CellID, ExGuid>> cellIDMappings,
            AtomicReference<HashMap<ExGuid, ExGuid> >revisionIDMappings)
    {
        manifestMappingGuid.set(null);
        cellIDMappings.set(null);
        revisionIDMappings.set(null);

        if (storageIndexExGuid == null)
        {
            return false;
        }

        DataElement storageIndexDataElement = dataElements.stream().filter(element -> element.dataElementExGuid.equals(storageIndexExGuid)).findAny().orElse(null);
        StorageIndexDataElementData storageIndexData = storageIndexDataElement.GetData<StorageIndexDataElementData>();
        manifestMappingGuid = storageIndexData.StorageIndexManifestMapping.ManifestMappingExtendedGUID;

        cellIDMappings = new HashMap<CellID, ExGuid>();
        foreach (StorageIndexCellMapping kv in storageIndexData.StorageIndexCellMappingList)
        {
            cellIDMappings.Add(kv.CellID, kv.CellMappingExtendedGUID);
        }

        revisionIDMappings = new HashMap<ExGuid, ExGuid>();
        foreach (StorageIndexRevisionMapping kv in storageIndexData.StorageIndexRevisionMappingList)
        {
            revisionIDMappings.Add(kv.RevisionExtendedGUID, kv.RevisionMappingExtendedGUID);
        }

        return true;
    }

    /// <summary>
    /// This method is used to get storage manifest data element from a list of data element.
    /// </summary>
    /// <param name="dataElements">Specify the data element list.</param>
    /// <param name="manifestMapping">Specify the manifest mapping GUID.</param>
    /// <returns>Return the storage manifest data element.</returns>
    public static StorageManifestDataElementData GetStorageManifestDataElementData(List<DataElement> dataElements, ExGuid manifestMapping)
    {
        DataElement storageManifestDataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(manifestMapping));
        if (storageManifestDataElement == null)
        {
            return null;
        }

        return storageManifestDataElement.GetData<StorageManifestDataElementData>();
    }

    /// <summary>
    /// This method is used to get cell manifest data element from a list of data element.
    /// </summary>
    /// <param name="dataElements">Specify the data element list.</param>
    /// <param name="manifestDataElementData">Specify the manifest data element.</param>
    /// <param name="cellIDMappings">Specify mapping of cell id.</param>
    /// <returns>Return the cell manifest data element.</returns>
    public static CellManifestDataElementData GetCellManifestDataElementData(List<DataElement> dataElements, StorageManifestDataElementData manifestDataElementData, HashMap<CellID, ExGuid> cellIDMappings)
    {
        CellID cellID = new CellID(new ExGuid(1u, RootExGuid), new ExGuid(1u, CellSecondExGuid));

        foreach (StorageManifestRootDeclare kv in manifestDataElementData.StorageManifestRootDeclareList)
        {
            if (kv.RootExtendedGUID.Equals(new ExGuid(2u, RootExGuid)) && kv.CellID.Equals(cellID))
            {
                if (!cellIDMappings.ContainsKey(kv.CellID))
                {
                    throw new InvalidOperationException(String.format("Cannot fin the Cell ID %s in the cell id mapping", cellID.ToString()));
                }

                ExGuid cellMappingID = cellIDMappings[kv.CellID];

                DataElement dataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(cellMappingID));
                if (dataElement == null)
                {
                    throw new InvalidOperationException("Cannot find the  cell data element with ExGuid " + cellMappingID.GUID.ToString());
                }

                return dataElement.GetData<CellManifestDataElementData>();
            }
        }

        throw new InvalidOperationException("Cannot find the CellManifestDataElement");
    }

    /// <summary>
    /// This method is used to get revision manifest data element from a list of data element.
    /// </summary>
    /// <param name="dataElements">Specify the data element list.</param>
    /// <param name="cellData">Specify the cell data element.</param>
    /// <param name="revisionIDMappings">Specify mapping of revision id.</param>
    /// <returns>Return the revision manifest data element.</returns>
    public static RevisionManifestDataElementData GetRevisionManifestDataElementData(List<DataElement> dataElements, CellManifestDataElementData cellData, HashMap<ExGuid, ExGuid> revisionIDMappings)
    {
        ExGuid currentRevisionExGuid = cellData.CellManifestCurrentRevision.CellManifestCurrentRevisionExtendedGUID;

        if (!revisionIDMappings.ContainsKey(currentRevisionExGuid))
        {
            throw new InvalidOperationException(String.format("Cannot find the revision id %s in the revisionMapping", currentRevisionExGuid.ToString()));
        }

        ExGuid revisionMapping = revisionIDMappings[currentRevisionExGuid];

        DataElement dataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(revisionMapping));
        if (dataElement == null)
        {
            throw new InvalidOperationException("Cannot find the revision data element with ExGuid " + revisionMapping.GUID.ToString());
        }

        return dataElement.GetData<RevisionManifestDataElementData>();
    }

    /// <summary>
    /// This method is used to get a list of object group data element from a list of data element.
    /// </summary>
    /// <param name="dataElements">Specify the data element list.</param>
    /// <param name="revisionData">Specify the revision data.</param>
    /// <param name="rootExGuid">Specify the root node object extended GUID.</param>
    /// <returns>Return the list of object group data element.</returns>
    public static List<ObjectGroupDataElementData> GetDataObjectDataElementData(List<DataElement> dataElements, RevisionManifestDataElementData revisionData, out ExGuid rootExGuid)
    {
        rootExGuid = null;

        foreach (RevisionManifestRootDeclare kv in revisionData.RevisionManifestRootDeclareList)
        {
            if (kv.RootExtendedGUID.Equals(new ExGuid(2u, RootExGuid)))
            {
                rootExGuid = kv.ObjectExtendedGUID;
                break;
            }
        }

        List<ObjectGroupDataElementData> dataList = new ArrayList<ObjectGroupDataElementData>();

        foreach (RevisionManifestObjectGroupReferences kv in revisionData.RevisionManifestObjectGroupReferencesList)
        {
            DataElement dataElement = dataElements.Find(element => element.DataElementExtendedGUID.Equals(kv.ObjectGroupExtendedGUID));
            if (dataElement == null)
            {
                throw new InvalidOperationException("Cannot find the object group data element with ExGuid " + kv.ObjectGroupExtendedGUID.GUID.ToString());
            }

            dataList.Add(dataElement.GetData<ObjectGroupDataElementData>());
        }

        return dataList;
    }
}
