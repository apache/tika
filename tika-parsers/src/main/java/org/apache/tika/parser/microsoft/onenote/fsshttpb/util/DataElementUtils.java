/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.microsoft.onenote.fsshttpb.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestCurrentRevision;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.IntermediateNodeObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.NodeObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestObjectGroupReferences;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestRootDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexCellMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexManifestMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexRevisionMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestRootDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestSchemaGUID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.SerialNumber;

public class DataElementUtils {
    public static final UUID RootExGuid = UUID.fromString("84DEFAB9-AAA3-4A0D-A3A8-520C77AC7073");
    public static final UUID CellSecondExGuid =
            UUID.fromString("6F2A4665-42C8-46C7-BAB4-E28FDCE1E32B");
    public static final UUID SchemaGuid = UUID.fromString("0EB93394-571D-41E9-AAD3-880D92D31955");

    /**
     * This method is used to build a list of data elements to represent a file.
     *
     * @param fileContent        The file content in byte array form.
     * @param storageIndexExGuid Reference set to the storage index ex guid.
     * @return List of DataElement objects that are stored in the file content.
     */
    public static List<DataElement> buildDataElements(byte[] fileContent,
                                                      AtomicReference<ExGuid> storageIndexExGuid)
            throws TikaException, IOException {
        List<ExGuid> objectDataExGuidList = new ArrayList<>();
        AtomicReference<ExGuid> rootNodeObjectExGuid = new AtomicReference<>();
        List<DataElement> dataElementList =
                createObjectGroupDataElement(fileContent, rootNodeObjectExGuid,
                        objectDataExGuidList);

        ExGuid baseRevisionID = new ExGuid(0, GuidUtil.emptyGuid());
        Map<ExGuid, ExGuid> revisionMapping = new HashMap<>();
        AtomicReference<ExGuid> currentRevisionID = new AtomicReference<>();
        dataElementList.add(
                createRevisionManifestDataElement(rootNodeObjectExGuid.get(), baseRevisionID,
                        objectDataExGuidList, revisionMapping, currentRevisionID));

        Map<CellID, ExGuid> cellIDMapping = new HashMap<>();
        dataElementList.add(createCellMainifestDataElement(currentRevisionID.get(), cellIDMapping));

        dataElementList.add(createStorageManifestDataElement(cellIDMapping));
        dataElementList.add(createStorageIndexDataElement(
                dataElementList.get(dataElementList.size() - 1).dataElementExGuid, cellIDMapping,
                revisionMapping));

        storageIndexExGuid.set(dataElementList.get(dataElementList.size() - 1).dataElementExGuid);
        return dataElementList;
    }

    /**
     * This method is used to create object group data/blob element list.
     *
     * @param fileContent          The file content in byte array format.
     * @param rootNodeExGuid       Output parameter to represent the root node extended GUID.
     * @param objectDataExGuidList Input/Output parameter to represent the list of extended GUID
     *                             for the data object data.
     * @return Return the list of data element which will represent the file content.
     */
    public static List<DataElement> createObjectGroupDataElement(byte[] fileContent,
                                                                 AtomicReference<ExGuid> rootNodeExGuid,
                                                                 List<ExGuid> objectDataExGuidList)
            throws TikaException, IOException {
        NodeObject rootNode = new IntermediateNodeObject.RootNodeObjectBuilder().Build(fileContent);

        // Storage the root object node ExGuid
        rootNodeExGuid.set(new ExGuid(rootNode.exGuid));
        List<DataElement> elements = new ObjectGroupDataElementData.Builder().build(rootNode);
        elements.stream().filter(element -> element.dataElementType ==
                        DataElementType.ObjectGroupDataElementData)
                .forEach(element -> objectDataExGuidList.add(element.dataElementExGuid));

        return elements;
    }

    /**
     * This method is used to create the revision manifest data element.
     *
     * @param rootObjectExGuid               Specify the root node object extended GUID.
     * @param baseRevisionID                 Specify the base revision Id.
     * @param refferenceObjectDataExGuidList Specify the reference object data extended list.
     * @param currentRevisionID              Input/output parameter to represent the mapping of revision manifest.
     * @param currentRevisionID              Output parameter to represent the revision GUID.
     * @return Return the revision manifest data element.
     */
    public static DataElement createRevisionManifestDataElement(ExGuid rootObjectExGuid,
                                                                ExGuid baseRevisionID,
                                                                List<ExGuid> refferenceObjectDataExGuidList,
                                                                Map<ExGuid, ExGuid> revisionMapping,
                                                                AtomicReference<ExGuid> currentRevisionID) {
        RevisionManifestDataElementData data = new RevisionManifestDataElementData();
        data.revisionManifest.revisionID = new ExGuid(1, UUID.randomUUID());
        data.revisionManifest.baseRevisionID = new ExGuid(baseRevisionID);

        // Set the root object data ExGuid
        RevisionManifestRootDeclare revisionManifestRootDeclare = new RevisionManifestRootDeclare();
        revisionManifestRootDeclare.rootExGuid = new ExGuid(2, RootExGuid);
        revisionManifestRootDeclare.objectExGuid = new ExGuid(rootObjectExGuid);
        data.revisionManifestRootDeclareList.add(revisionManifestRootDeclare);

        // Set all the reference object data
        if (refferenceObjectDataExGuidList != null) {
            for (ExGuid dataGuid : refferenceObjectDataExGuidList) {
                data.revisionManifestObjectGroupReferences.add(
                        new RevisionManifestObjectGroupReferences(dataGuid));
            }
        }

        DataElement dataElement =
                new DataElement(DataElementType.RevisionManifestDataElementData, data);
        revisionMapping.put(data.revisionManifest.revisionID, dataElement.dataElementExGuid);
        currentRevisionID.set(data.revisionManifest.revisionID);
        return dataElement;
    }

    /**
     * This method is used to create the cell manifest data element.
     *
     * @param revisionId    Specify the revision GUID.
     * @param cellIDMapping Input/output parameter to represent the mapping of cell manifest.
     * @return Return the cell manifest data element.
     */
    public static DataElement createCellMainifestDataElement(ExGuid revisionId,
                                                             Map<CellID, ExGuid> cellIDMapping) {
        CellManifestDataElementData data = new CellManifestDataElementData();
        data.cellManifestCurrentRevision = new CellManifestCurrentRevision();
        data.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid = new ExGuid(revisionId);
        DataElement dataElement =
                new DataElement(DataElementType.CellManifestDataElementData, data);

        CellID cellID = new CellID(new ExGuid(1, RootExGuid), new ExGuid(1, CellSecondExGuid));
        cellIDMapping.put(cellID, dataElement.dataElementExGuid);
        return dataElement;
    }

    /**
     * This method is used to create the storage manifest data element.
     *
     * @param cellIDMapping Specify the mapping of cell manifest.
     * @return The storage manifest data element.
     */
    public static DataElement createStorageManifestDataElement(Map<CellID, ExGuid> cellIDMapping) {
        StorageManifestDataElementData data = new StorageManifestDataElementData();
        data.storageManifestSchemaGUID = new StorageManifestSchemaGUID();
        data.storageManifestSchemaGUID.guid = SchemaGuid;

        for (Map.Entry<CellID, ExGuid> kv : cellIDMapping.entrySet()) {
            StorageManifestRootDeclare manifestRootDeclare = new StorageManifestRootDeclare();
            manifestRootDeclare.rootExGUID = new ExGuid(2, RootExGuid);
            manifestRootDeclare.cellID = new CellID(kv.getKey());
            data.storageManifestRootDeclareList.add(manifestRootDeclare);
        }

        return new DataElement(DataElementType.StorageManifestDataElementData, data);
    }

    /**
     * This method is used to create the storage index data element.
     *
     * @param manifestExGuid     Specify the storage manifest data element extended GUID.
     * @param cellIDMappings     Specify the mapping of cell manifest.
     * @param revisionIDMappings Specify the mapping of revision manifest.
     * @return The storage index data element.
     */
    public static DataElement createStorageIndexDataElement(ExGuid manifestExGuid,
                                                            Map<CellID, ExGuid> cellIDMappings,
                                                            Map<ExGuid, ExGuid> revisionIDMappings) {
        StorageIndexDataElementData data = new StorageIndexDataElementData();

        data.storageIndexManifestMapping = new StorageIndexManifestMapping();
        data.storageIndexManifestMapping.manifestMappingExGuid = new ExGuid(manifestExGuid);
        data.storageIndexManifestMapping.manifestMappingSerialNumber =
                new SerialNumber(UUID.randomUUID(),
                        SequenceNumberGenerator.GetCurrentSerialNumber());

        for (Map.Entry<CellID, ExGuid> kv : cellIDMappings.entrySet()) {
            StorageIndexCellMapping cellMapping = new StorageIndexCellMapping();
            cellMapping.cellID = kv.getKey();
            cellMapping.cellMappingExGuid = kv.getValue();
            cellMapping.cellMappingSerialNumber = new SerialNumber(UUID.randomUUID(),
                    SequenceNumberGenerator.GetCurrentSerialNumber());
            data.storageIndexCellMappingList.add(cellMapping);
        }

        for (Map.Entry<ExGuid, ExGuid> kv : revisionIDMappings.entrySet()) {
            StorageIndexRevisionMapping revisionMapping = new StorageIndexRevisionMapping();
            revisionMapping.revisionExGuid = kv.getKey();
            revisionMapping.revisionMappingExGuid = kv.getValue();
            revisionMapping.revisionMappingSerialNumber = new SerialNumber(UUID.randomUUID(),
                    SequenceNumberGenerator.GetCurrentSerialNumber());
            data.storageIndexRevisionMappingList.add(revisionMapping);
        }

        return new DataElement(DataElementType.StorageIndexDataElementData, data);
    }

    /**
     * This method is used to get the list of object group data element from a list of data element.
     *
     * @param dataElements       Specify the data element list.
     * @param storageIndexExGuid Specify the storage index extended GUID.
     * @param rootExGuid         Output parameter to represent the root node object.
     * @return Return the list of object group data elements.
     */
    public static List<ObjectGroupDataElementData> getDataObjectDataElementData(
            List<DataElement> dataElements, ExGuid storageIndexExGuid,
            AtomicReference<ExGuid> rootExGuid) throws TikaException {
        AtomicReference<ExGuid> manifestMappingGuid = new AtomicReference<>();
        AtomicReference<HashMap<CellID, ExGuid>> cellIDMappings = new AtomicReference<>();
        AtomicReference<HashMap<ExGuid, ExGuid>> revisionIDMappings = new AtomicReference<>();
        analyzeStorageIndexDataElement(dataElements, storageIndexExGuid, manifestMappingGuid,
                cellIDMappings, revisionIDMappings);
        StorageManifestDataElementData manifestData =
                getStorageManifestDataElementData(dataElements, manifestMappingGuid.get());
        if (manifestData == null) {
            throw new InvalidOperationException(
                    "Cannot find the storage manifest data element with ExGuid " +
                            manifestMappingGuid.get().guid.toString());
        }

        CellManifestDataElementData cellData =
                getCellManifestDataElementData(dataElements, manifestData, cellIDMappings.get());
        RevisionManifestDataElementData revisionData =
                getRevisionManifestDataElementData(dataElements, cellData,
                        revisionIDMappings.get());
        return getDataObjectDataElementData(dataElements, revisionData, rootExGuid);
    }

    /**
     * This method is used to try to analyze the returned whether data elements are complete.
     *
     * @param dataElements       Specify the data elements list.
     * @param storageIndexExGuid Specify the storage index extended GUID.
     * @return If the data elements start with the specified storage index extended GUID are complete,
     * return true. Otherwise return false.
     */
    public static boolean tryAnalyzeWhetherFullDataElementList(List<DataElement> dataElements,
                                                               ExGuid storageIndexExGuid)
            throws TikaException {
        AtomicReference<ExGuid> manifestMappingGuid = new AtomicReference<>();
        AtomicReference<HashMap<CellID, ExGuid>> cellIDMappings = new AtomicReference<>();
        AtomicReference<HashMap<ExGuid, ExGuid>> revisionIDMappings = new AtomicReference<>();
        if (!analyzeStorageIndexDataElement(dataElements, storageIndexExGuid, manifestMappingGuid,
                cellIDMappings, revisionIDMappings)) {
            return false;
        }

        if (cellIDMappings.get().size() == 0) {
            return false;
        }

        if (revisionIDMappings.get().size() == 0) {
            return false;
        }

        StorageManifestDataElementData manifestData =
                getStorageManifestDataElementData(dataElements, manifestMappingGuid.get());
        if (manifestData == null) {
            return false;
        }

        for (StorageManifestRootDeclare kv : manifestData.storageManifestRootDeclareList) {
            if (!cellIDMappings.get().containsKey(kv.cellID)) {
                throw new InvalidOperationException(String.format(Locale.US,
                        "Cannot find the Cell ID %s in the cell id mapping", kv.cellID.toString()));
            }

            ExGuid cellMappingID = cellIDMappings.get().get(kv.cellID);
            DataElement dataElement = dataElements.stream()
                    .filter(element -> element.dataElementExGuid.equals(cellMappingID)).findAny()
                    .orElse(null);
            if (dataElement == null) {
                return false;
            }

            CellManifestDataElementData cellData =
                    dataElement.getData(CellManifestDataElementData.class);
            ExGuid currentRevisionExGuid =
                    cellData.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid;
            if (!revisionIDMappings.get().containsKey(currentRevisionExGuid)) {
                throw new InvalidOperationException(String.format(Locale.US,
                        "Cannot find the revision id %s in the revisionMapping",
                        currentRevisionExGuid.toString()));
            }

            ExGuid revisionMapping = revisionIDMappings.get().get(currentRevisionExGuid);
            dataElement = dataElements.stream()
                    .filter(element -> element.dataElementExGuid.equals(revisionMapping)).findAny()
                    .orElse(null);
            if (dataElement == null) {
                return false;
            }

            RevisionManifestDataElementData revisionData =
                    dataElement.getData(RevisionManifestDataElementData.class);
            for (RevisionManifestObjectGroupReferences reference :
                    revisionData.revisionManifestObjectGroupReferences) {
                dataElement = dataElements.stream()
                        .filter(element -> element.dataElementExGuid.equals(
                                reference.objectGroupExtendedGUID)).findAny().orElse(null);
                if (dataElement == null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * This method is used to analyze whether the data elements are confirmed to the schema defined in MS-FSSHTTPD.
     *
     * @param dataElements       Specify the data elements list.
     * @param storageIndexExGuid Specify the storage index extended GUID.
     * @return If the data elements confirms to the schema defined in the MS-FSSHTTPD returns true, otherwise false.
     */
    public static boolean tryAnalyzeWhetherConfirmSchema(List<DataElement> dataElements,
                                                         ExGuid storageIndexExGuid)
            throws TikaException {
        DataElement storageIndexDataElement = dataElements.stream()
                .filter(element -> element.dataElementExGuid.equals(storageIndexExGuid)).findAny()
                .orElse(null);
        if (storageIndexExGuid == null) {
            return false;
        }

        StorageIndexDataElementData storageIndexData =
                storageIndexDataElement.getData(StorageIndexDataElementData.class);
        ExGuid manifestMappingGuid =
                storageIndexData.storageIndexManifestMapping.manifestMappingExGuid;

        DataElement storageManifestDataElement = dataElements.stream()
                .filter(element -> element.dataElementExGuid.equals(manifestMappingGuid)).findAny()
                .orElse(null);
        if (storageManifestDataElement == null) {
            return false;
        }

        return SchemaGuid.equals(storageManifestDataElement.getData(
                StorageManifestDataElementData.class).storageManifestSchemaGUID.guid);
    }

    /**
     * This method is used to analyze the storage index data element to get all the mappings.
     *
     * @param dataElements        Specify the data element list.
     * @param storageIndexExGuid  Specify the storage index extended GUID.
     * @param manifestMappingGuid Output parameter to represent the storage manifest mapping GUID.
     * @param cellIDMappings      Output parameter to represent the mapping of cell id.
     * @param revisionIDMappings  Output parameter to represent the revision id.
     * @return Return true if analyze the storage index succeeds, otherwise return false.
     */
    public static boolean analyzeStorageIndexDataElement(List<DataElement> dataElements,
                                                         ExGuid storageIndexExGuid,
                                                         AtomicReference<ExGuid> manifestMappingGuid,
                                                         AtomicReference<HashMap<CellID, ExGuid>> cellIDMappings,
                                                         AtomicReference<HashMap<ExGuid, ExGuid>> revisionIDMappings)
            throws TikaException {
        manifestMappingGuid.set(null);
        cellIDMappings.set(null);
        revisionIDMappings.set(null);

        if (storageIndexExGuid == null) {
            return false;
        }

        DataElement storageIndexDataElement = dataElements.stream()
                .filter(element -> element.dataElementExGuid.equals(storageIndexExGuid)).findAny()
                .orElse(null);
        StorageIndexDataElementData storageIndexData =
                storageIndexDataElement.getData(StorageIndexDataElementData.class);
        manifestMappingGuid.set(storageIndexData.storageIndexManifestMapping.manifestMappingExGuid);

        cellIDMappings.set(new HashMap<>());
        for (StorageIndexCellMapping kv : storageIndexData.storageIndexCellMappingList) {
            cellIDMappings.get().put(kv.cellID, kv.cellMappingExGuid);
        }

        revisionIDMappings.set(new HashMap<>());
        for (StorageIndexRevisionMapping kv : storageIndexData.storageIndexRevisionMappingList) {
            revisionIDMappings.get().put(kv.revisionExGuid, kv.revisionMappingExGuid);
        }

        return true;
    }

    /**
     * This method is used to get storage manifest data element from a list of data element.
     *
     * @param dataElements    Specify the data element list.
     * @param manifestMapping Specify the manifest mapping GUID.
     * @return Return the storage manifest data element.
     */
    public static StorageManifestDataElementData getStorageManifestDataElementData(
            List<DataElement> dataElements, ExGuid manifestMapping) throws TikaException {
        DataElement storageManifestDataElement = dataElements.stream()
                .filter(element -> element.dataElementExGuid.equals(manifestMapping)).findAny()
                .orElse(null);
        if (storageManifestDataElement == null) {
            return null;
        }

        return storageManifestDataElement.getData(StorageManifestDataElementData.class);
    }

    /**
     * This method is used to get cell manifest data element from a list of data element.
     *
     * @param dataElements            Specify the data element list.
     * @param manifestDataElementData Specify the manifest data element.
     * @param cellIDMappings          Specify mapping of cell id.
     * @return Return the cell manifest data element.
     */
    public static CellManifestDataElementData getCellManifestDataElementData(
            List<DataElement> dataElements, StorageManifestDataElementData manifestDataElementData,
            HashMap<CellID, ExGuid> cellIDMappings) throws TikaException {
        CellID cellID = new CellID(new ExGuid(1, RootExGuid), new ExGuid(1, CellSecondExGuid));

        for (StorageManifestRootDeclare kv :
                manifestDataElementData.storageManifestRootDeclareList) {
            if (kv.rootExGUID.equals(new ExGuid(2, RootExGuid)) && kv.cellID.equals(cellID)) {
                if (!cellIDMappings.containsKey(kv.cellID)) {
                    throw new InvalidOperationException(String.format(Locale.US,
                            "Cannot fin the Cell ID %s in the cell id mapping", cellID));
                }

                ExGuid cellMappingID = cellIDMappings.get(kv.cellID);

                DataElement dataElement = dataElements.stream()
                        .filter(element -> element.dataElementExGuid.equals(cellMappingID))
                        .findAny().orElse(null);
                if (dataElement == null) {
                    throw new InvalidOperationException(
                            "Cannot find the  cell data element with ExGuid " +
                                    cellMappingID.guid.toString());
                }

                return dataElement.getData(CellManifestDataElementData.class);
            }
        }

        throw new InvalidOperationException("Cannot find the CellManifestDataElement");
    }

    /**
     * This method is used to get revision manifest data element from a list of data element.
     *
     * @param dataElements       Specify the data element list.
     * @param cellData           Specify the cell data element.
     * @param revisionIDMappings Specify mapping of revision id.
     * @return Return the revision manifest data element.
     */
    public static RevisionManifestDataElementData getRevisionManifestDataElementData(
            List<DataElement> dataElements, CellManifestDataElementData cellData,
            HashMap<ExGuid, ExGuid> revisionIDMappings) throws TikaException {
        ExGuid currentRevisionExGuid =
                cellData.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid;

        if (!revisionIDMappings.containsKey(currentRevisionExGuid)) {
            throw new InvalidOperationException(String.format(Locale.US,
                    "Cannot find the revision id %s in the revisionMapping",
                    currentRevisionExGuid.toString()));
        }

        ExGuid revisionMapping = revisionIDMappings.get(currentRevisionExGuid);

        DataElement dataElement = dataElements.stream()
                .filter(element -> element.dataElementExGuid.equals(revisionMapping)).findAny()
                .orElse(null);
        if (dataElement == null) {
            throw new InvalidOperationException(
                    "Cannot find the revision data element with ExGuid " + revisionMapping.guid);
        }

        return dataElement.getData(RevisionManifestDataElementData.class);
    }

    /**
     * This method is used to get a list of object group data element from a list of data element.
     *
     * @param dataElements Specify the data element list.
     * @param revisionData Specify the revision data.
     * @param rootExGuid   Specify the root node object extended GUID.
     * @return Return the list of object group data element.
     */
    public static List<ObjectGroupDataElementData> getDataObjectDataElementData(
            List<DataElement> dataElements, RevisionManifestDataElementData revisionData,
            AtomicReference<ExGuid> rootExGuid) throws TikaException {
        rootExGuid = null;

        for (RevisionManifestRootDeclare kv : revisionData.revisionManifestRootDeclareList) {
            if (kv.rootExGuid.equals(new ExGuid(2, RootExGuid))) {
                rootExGuid.set(kv.objectExGuid);
                break;
            }
        }

        List<ObjectGroupDataElementData> dataList = new ArrayList<>();

        for (RevisionManifestObjectGroupReferences kv : revisionData.revisionManifestObjectGroupReferences) {
            DataElement dataElement = dataElements.stream()
                    .filter(element -> element.dataElementExGuid.equals(kv.objectGroupExtendedGUID))
                    .findAny().orElse(null);
            if (dataElement == null) {
                throw new InvalidOperationException(
                        "Cannot find the object group data element with ExGuid " +
                                kv.objectGroupExtendedGUID.guid.toString());
            }

            dataList.add(dataElement.getData(ObjectGroupDataElementData.class));
        }

        return dataList;
    }
}
