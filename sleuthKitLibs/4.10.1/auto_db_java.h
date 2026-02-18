/*
 ** The Sleuth Kit 
 **
 ** Brian Carrier [carrier <at> sleuthkit [dot] org]
 ** Copyright (c) 2020 Brian Carrier.  All Rights reserved
 **
 ** This software is distributed under the Common Public License 1.0
 **
 */

/**
 * \file auto_db_java.h
 * Contains the class that creates a case-level database of file system
 * data from the JNI code.
 */

#ifndef _AUTO_DB_JAVA_H
#define _AUTO_DB_JAVA_H

#include <map>
using std::map;

#include <string>
using std::string;

#include "tsk/auto/tsk_auto_i.h"
#include "tsk/auto/tsk_db.h"
#include "jni.h"


/** \internal
 * C++ class that implements TskAuto to load file metadata into a database. 
 * This is used by the TskCaseDb class. 
 */
class TskAutoDbJava :public TskAuto {
  public:
    TskAutoDbJava();
    virtual ~TskAutoDbJava();
    virtual uint8_t openImage(int, const TSK_TCHAR * const images[],
        TSK_IMG_TYPE_ENUM, unsigned int a_ssize, const char* deviceId = NULL);
    virtual uint8_t openImage(const char* a_deviceId = NULL);
    virtual uint8_t openImageUtf8(int, const char *const images[],
        TSK_IMG_TYPE_ENUM, unsigned int a_ssize, const char* deviceId = NULL);
    virtual void closeImage();
    void close();
    virtual void setTz(string tzone);
    virtual void setDatasourceObjId(int64_t img_id);

    virtual TSK_FILTER_ENUM filterVs(const TSK_VS_INFO * vs_info);
    virtual TSK_FILTER_ENUM filterVol(const TSK_VS_PART_INFO * vs_part);
    virtual TSK_FILTER_ENUM filterPool(const TSK_POOL_INFO * pool_info);
    virtual TSK_FILTER_ENUM filterPoolVol(const TSK_POOL_VOLUME_INFO * pool_vol);
    virtual TSK_FILTER_ENUM filterFs(TSK_FS_INFO * fs_info);
    virtual TSK_RETVAL_ENUM processFile(TSK_FS_FILE * fs_file,
        const char *path);
    const std::string getCurDir();

    /**
     * Sets whether or not the file systems for an image should be added when 
     * the image is added to the case database. The default value is true. 
     */
    void setAddFileSystems(bool addFileSystems);

    /**
     * Skip processing of orphans on FAT filesystems.  
     * This will make the loading of the database much faster
     * but you will not have all deleted files.  Default value is false. 
     * @param noFatFsOrphans flag set to true if to skip processing orphans on FAT fs
     */
    virtual void setNoFatFsOrphans(bool noFatFsOrphans);

    /**
     * When enabled, records for unallocated file system space will be added to the database. Default value is false.
     * @param addUnallocSpace If true, create records for contiguous unallocated file system sectors. 
     */
    virtual void setAddUnallocSpace(bool addUnallocSpace);

    /**
     * When enabled, records for unallocated file system space will be added to the database. Default value is false.
     * @param addUnallocSpace If true, create records for contiguous unallocated file system sectors.
     * @param minChunkSize the number of bytes to group unallocated data into. A value of 0 will create
     * one large chunk and group only on volume boundaries. A value of -1 will group each consecutive
     * chunk.
     */
    virtual void setAddUnallocSpace(bool addUnallocSpace, int64_t minChunkSize);

    /**
    * When enabled, records for unallocated file system space will be added to the database with the given parameters.
    * Automatically sets the flag to create records for contiguous unallocated file system sectors.
    * @param minChunkSize the number of bytes to group unallocated data into. A value of 0 will create
    * one large chunk and group only on volume boundaries. A value of -1 will group each consecutive
    * chunk.
    * @param maxChunkSize the maximum number of bytes in one record of unallocated data. A value of -1 will not
    * split the records based on size
    */
    virtual void setAddUnallocSpace(int64_t minChunkSize, int64_t maxChunkSize);

    uint8_t addFilesInImgToDb();

    /**
     * 
     */
    uint8_t startAddImage(int numImg, const TSK_TCHAR * const imagePaths[],
        TSK_IMG_TYPE_ENUM imgType, unsigned int sSize, const char* deviceId = NULL);
    uint8_t startAddImage(TSK_IMG_INFO * img_info, const char* deviceId = NULL);
#ifdef WIN32
    uint8_t startAddImage(int numImg, const char *const imagePaths[],
        TSK_IMG_TYPE_ENUM imgType, unsigned int sSize, const char* deviceId = NULL);
#endif
    void stopAddImage();

    int64_t getImageID();

    TSK_RETVAL_ENUM initializeJni(JNIEnv *, jobject);

  private:
    int64_t m_curImgId;     ///< Object ID of image currently being processed
    int64_t m_curVsId;      ///< Object ID of volume system currently being processed
    int64_t m_curVolId;     ///< Object ID of volume currently being processed
    int64_t m_curPoolVol;   ///< Object ID of the pool volume currently being processed
    int64_t m_curPoolVs;    ///< Object ID of the pool volume system currently being processed
    int64_t m_curFsId;      ///< Object ID of file system currently being processed
    int64_t m_curFileId;    ///< Object ID of file currently being processed
    TSK_INUM_T m_curDirAddr;		///< Meta address the directory currently being processed
    int64_t m_curUnallocDirId;	
    string m_curDirPath;		//< Path of the current directory being processed
    tsk_lock_t m_curDirPathLock; //< protects concurrent access to m_curDirPath
    string m_curImgTZone;
    bool m_vsFound;
    bool m_volFound;
    bool m_poolFound;
    bool m_stopped;
    bool m_addFileSystems;
    bool m_noFatFsOrphans;
    bool m_addUnallocSpace;
    int64_t m_minChunkSize; ///< -1 for no minimum, 0 for no chunking at all, greater than 0 to wait for that number of chunks before writing to the database 
    int64_t m_maxChunkSize; ///< Max number of unalloc bytes to process before writing to the database, even if there is no natural break. -1 for no chunking
    bool m_foundStructure;  ///< Set to true when we find either a volume or file system
    bool m_attributeAdded; ///< Set to true when an attribute was added by processAttributes

    // These are used to write unallocated blocks for pools at the end of the add image
    // process. We can't load the pool_info objects directly from the database so we will
    // store info about them here.
    std::map<int64_t, int64_t> m_poolOffsetToParentId;
    std::map<int64_t, int64_t> m_poolOffsetToVsId;

    // JNI data
    JNIEnv * m_jniEnv = NULL;
    jclass m_callbackClass = NULL;
    jobject m_javaDbObj = NULL;
    jmethodID m_addImageMethodID = NULL;
    jmethodID m_addImageNameMethodID = NULL;
    jmethodID m_addAcquisitionDetailsMethodID = NULL;
    jmethodID m_addVolumeSystemMethodID = NULL;
    jmethodID m_addVolumeMethodID = NULL;
    jmethodID m_addPoolMethodID = NULL;
    jmethodID m_addFileSystemMethodID = NULL;
    jmethodID m_addFileMethodID = NULL;
    jmethodID m_addUnallocParentMethodID = NULL;
    jmethodID m_addLayoutFileMethodID = NULL;
    jmethodID m_addLayoutFileRangeMethodID = NULL;

    // Cached objects
    vector<TSK_DB_FS_INFO> m_savedFsInfo;
    vector<TSK_DB_VS_INFO> m_savedVsInfo;
    vector<TSK_DB_VS_PART_INFO> m_savedVsPartInfo;
    vector<TSK_DB_OBJECT> m_savedObjects;

    void saveObjectInfo(int64_t objId, int64_t parObjId, TSK_DB_OBJECT_TYPE_ENUM type);
    TSK_RETVAL_ENUM getObjectInfo(int64_t objId, TSK_DB_OBJECT** obj_info);

    TSK_RETVAL_ENUM createJString(const char * inputString, jstring & newJString);

    // prevent copying until we add proper logic to handle it
    TskAutoDbJava(const TskAutoDbJava&);
    TskAutoDbJava & operator=(const TskAutoDbJava&);

    //internal structure to keep track of temp. unalloc block range
    typedef struct _UNALLOC_BLOCK_WLK_TRACK {
        _UNALLOC_BLOCK_WLK_TRACK(TskAutoDbJava & tskAutoDbJava, const TSK_FS_INFO & fsInfo, const int64_t fsObjId, int64_t minChunkSize, int64_t maxChunkSize)
            : tskAutoDbJava(tskAutoDbJava),fsInfo(fsInfo),fsObjId(fsObjId),curRangeStart(0), minChunkSize(minChunkSize), maxChunkSize(maxChunkSize), prevBlock(0), isStart(true), nextSequenceNo(0) {}
        TskAutoDbJava & tskAutoDbJava;
        const TSK_FS_INFO & fsInfo;
        const int64_t fsObjId;
        vector<TSK_DB_FILE_LAYOUT_RANGE> ranges;																																										
        TSK_DADDR_T curRangeStart;
        int64_t size;
        const int64_t minChunkSize;
        const int64_t maxChunkSize;
        TSK_DADDR_T prevBlock;
        bool isStart;
        uint32_t nextSequenceNo;
    } UNALLOC_BLOCK_WLK_TRACK;

    uint8_t addImageDetails(const char *);
    TSK_RETVAL_ENUM insertFileData(TSK_FS_FILE * fs_file,
        const TSK_FS_ATTR *, const char *path);
    virtual TSK_RETVAL_ENUM processAttribute(TSK_FS_FILE *,
        const TSK_FS_ATTR * fs_attr, const char *path);

    TSK_RETVAL_ENUM addUnallocatedPoolBlocksToDb(size_t & numPool);
    static TSK_WALK_RET_ENUM fsWalkUnallocBlocksCb(const TSK_FS_BLOCK *a_block, void *a_ptr);
    TSK_RETVAL_ENUM addFsInfoUnalloc(const TSK_DB_FS_INFO & dbFsInfo);
    TSK_RETVAL_ENUM addUnallocFsSpaceToDb(size_t & numFs);
    TSK_RETVAL_ENUM addUnallocVsSpaceToDb(size_t & numVsP);
    TSK_RETVAL_ENUM addUnallocImageSpaceToDb();
    TSK_RETVAL_ENUM addUnallocSpaceToDb();

    // JNI methods
    TSK_RETVAL_ENUM addImageInfo(int type, TSK_OFF_T ssize, int64_t & objId, const string & timezone, TSK_OFF_T size, const string &md5,
        const string& sha1, const string& sha256, const string& deviceId, const string& collectionDetails, char** img_ptrs, int num_imgs);
    void addAcquisitionDetails(int64_t imgId, const string& collectionDetails);
    TSK_RETVAL_ENUM addVsInfo(const TSK_VS_INFO* vs_info, int64_t parObjId, int64_t& objId);
    TSK_RETVAL_ENUM addPoolInfoAndVS(const TSK_POOL_INFO *pool_info, int64_t parObjId, int64_t& objId);
    TSK_RETVAL_ENUM addPoolVolumeInfo(const TSK_POOL_VOLUME_INFO* pool_vol, int64_t parObjId, int64_t& objId);
    TSK_RETVAL_ENUM addVolumeInfo(const TSK_VS_PART_INFO* vs_part, int64_t parObjId, int64_t& objId);
    TSK_RETVAL_ENUM addFsInfo(const TSK_FS_INFO* fs_info, int64_t parObjId, int64_t& objId);
    TSK_RETVAL_ENUM addFsFile(TSK_FS_FILE* fs_file,
        const TSK_FS_ATTR* fs_attr, const char* path,
        int64_t fsObjId, int64_t& objId, int64_t dataSourceObjId);
    TSK_RETVAL_ENUM addFile(TSK_FS_FILE* fs_file,
        const TSK_FS_ATTR* fs_attr, const char* path,
        int64_t fsObjId, int64_t parObjId,
        int64_t dataSourceObjId);
    TSK_RETVAL_ENUM addFileWithLayoutRange(const TSK_DB_FILES_TYPE_ENUM dbFileType, const int64_t parentObjId,
        const int64_t fsObjId, const uint64_t size,
        vector<TSK_DB_FILE_LAYOUT_RANGE>& ranges, int64_t& objId,
        int64_t dataSourceObjId);
    TSK_RETVAL_ENUM addUnallocBlockFile(const int64_t parentObjId, const int64_t fsObjId, const uint64_t size,
        vector<TSK_DB_FILE_LAYOUT_RANGE>& ranges, int64_t& objId,
        int64_t dataSourceObjId);
    TSK_RETVAL_ENUM addUnusedBlockFile(const int64_t parentObjId, const int64_t fsObjId, const uint64_t size,
        vector<TSK_DB_FILE_LAYOUT_RANGE>& ranges, int64_t& objId,
        int64_t dataSourceObjId);
    TSK_RETVAL_ENUM addUnallocFsBlockFilesParent(const int64_t fsObjId, int64_t& objId, int64_t dataSourceObjId);
    TSK_RETVAL_ENUM addUnallocatedPoolVolume(int vol_index, int64_t parObjId, int64_t& objId);

};

#endif
