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
 * \file auto_db_java.cpp
 * Contains code to populate database with volume and file system information from a specific image.
 */

#include "auto_db_java.h"
#include "jni.h"
#include "tsk/img/img_writer.h"
#if HAVE_LIBEWF
#include "tsk/img/ewf.h"
#include "tsk/img/tsk_img_i.h"
#endif
#include <string.h>

#include <algorithm>
#include <sstream>

using std::stringstream;
using std::for_each;

/**
 */
TskAutoDbJava::TskAutoDbJava()
{
    m_curImgId = 0;
    m_curVsId = 0;
    m_curVolId = 0;
    m_curFsId = 0;
    m_curFileId = 0;
    m_curUnallocDirId = 0;
    m_curDirAddr = 0;
    m_curDirPath = "";
    m_vsFound = false;
    m_volFound = false;
    m_poolFound = false;
    m_stopped = false;
    m_foundStructure = false;
    m_attributeAdded = false;
    m_addFileSystems = true;
    m_noFatFsOrphans = false;
    m_addUnallocSpace = false;
    m_minChunkSize = -1;
    m_maxChunkSize = -1;

    m_jniEnv = NULL;

    tsk_init_lock(&m_curDirPathLock);
}

TskAutoDbJava::~TskAutoDbJava()
{
    closeImage();
    tsk_deinit_lock(&m_curDirPathLock);
}

/**
* Look up all callback method IDs
* @param jniEnv pointer to java environment this was called from
* @param jobj the TskCaseDbBridge object this was called from
*/
TSK_RETVAL_ENUM
TskAutoDbJava::initializeJni(JNIEnv * jniEnv, jobject jobj) {
    m_jniEnv = jniEnv;
    m_javaDbObj = m_jniEnv->NewGlobalRef(jobj);

    jclass localCallbackClass = m_jniEnv->FindClass("org/sleuthkit/datamodel/TskCaseDbBridge");
    if (localCallbackClass == NULL) {
        return TSK_ERR;
    }
    m_callbackClass = (jclass)m_jniEnv->NewGlobalRef(localCallbackClass);

    m_addImageMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addImageInfo", "(IJLjava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)J");
    if (m_addImageMethodID == NULL) {
        return TSK_ERR;
    }

    m_addAcquisitionDetailsMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addAcquisitionDetails", "(JLjava/lang/String;)V");
    if (m_addAcquisitionDetailsMethodID == NULL) {
        return TSK_ERR;
    }

    m_addVolumeSystemMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addVsInfo", "(JIJJ)J");
    if (m_addVolumeSystemMethodID == NULL) {
        return TSK_ERR;
    }

    m_addVolumeMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addVolume", "(JJJJLjava/lang/String;J)J");
    if (m_addVolumeMethodID == NULL) {
        return TSK_ERR;
    }

    m_addPoolMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addPool", "(JI)J");
    if (m_addPoolMethodID == NULL) {
        return TSK_ERR;
    }


    m_addFileSystemMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addFileSystem", "(JJIJJJJJ)J");
    if (m_addFileSystemMethodID == NULL) {
        return TSK_ERR;
    }

    m_addFileMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addFile", "(JJJIIILjava/lang/String;JJIIIIJJJJJIIILjava/lang/String;Ljava/lang/String;JJJ)J");
    if (m_addFileMethodID == NULL) {
        return TSK_ERR;
    }

    m_addUnallocParentMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addUnallocFsBlockFilesParent", "(JLjava/lang/String;)J");
    if (m_addUnallocParentMethodID == NULL) {
        return TSK_ERR;
    }

    m_addLayoutFileMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addLayoutFile", "(JJJILjava/lang/String;J)J");
    if (m_addLayoutFileMethodID == NULL) {
        return TSK_ERR;
    }
    
    m_addLayoutFileRangeMethodID = m_jniEnv->GetMethodID(m_callbackClass, "addLayoutFileRange", "(JJJJ)J");
    if (m_addLayoutFileRangeMethodID == NULL) {
        return TSK_ERR;
    }
    return TSK_OK;
}

/**
* Cache a database object for later use. Should be called on image, volume system, volume,
* pool, and file system.
* @param objId    The object ID of the new object
* @param parObjId The object ID of the new object's parent
* @param type     The type of object
*/
void 
TskAutoDbJava::saveObjectInfo(int64_t objId, int64_t parObjId, TSK_DB_OBJECT_TYPE_ENUM type) {
    TSK_DB_OBJECT objectInfo;
    objectInfo.objId = objId;
    objectInfo.parObjId = parObjId;
    objectInfo.type = type;
    m_savedObjects.push_back(objectInfo);
}

/**
* Get a previously cached database object.
* @param objId The object ID of the object being loaded
*/
TSK_RETVAL_ENUM 
TskAutoDbJava::getObjectInfo(int64_t objId, TSK_DB_OBJECT** obj_info) {
    for (vector<TSK_DB_OBJECT>::iterator itObjs = m_savedObjects.begin();
            itObjs != m_savedObjects.end(); ++itObjs) {
        TSK_DB_OBJECT* tskDbObj = &(*itObjs);
        if (tskDbObj->objId == objId) {
            *obj_info = tskDbObj;
            return TSK_OK;
        }
    }

    // Object not found
    return TSK_ERR;
}

/**
* Adds image details to the existing database tables. Object ID for new image stored in objId.
*
* @param type Image type
* @param ssize Size of device sector in bytes (or 0 for default)
* @param objId The object id assigned to the image (out param)
* @param timeZone The timezone the image is from
* @param size The size of the image in bytes.
* @param md5 MD5 hash of the image
* @param sha1 SHA1 hash of the image
* @param sha256 SHA256 hash of the image
* @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID).
* @param collectionDetails collection details
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addImageInfo(int type, TSK_OFF_T ssize, int64_t & objId, const string & timezone, TSK_OFF_T size, const string &md5,
    const string& sha1, const string& sha256, const string& deviceId, const string& collectionDetails,
    char** img_ptrs, int num_imgs) {

    const char *tz_cstr = timezone.c_str();
    jstring tzj = m_jniEnv->NewStringUTF(tz_cstr);

    const char *md5_cstr = md5.c_str();
    jstring md5j = m_jniEnv->NewStringUTF(md5_cstr);

    const char *sha1_cstr = sha1.c_str();
    jstring sha1j = m_jniEnv->NewStringUTF(sha1_cstr);

    const char *sha256_cstr = sha256.c_str();
    jstring sha256j = m_jniEnv->NewStringUTF(sha256_cstr);

    const char *devId_cstr = deviceId.c_str();
    jstring devIdj = m_jniEnv->NewStringUTF(devId_cstr);

    const char *coll_cstr = collectionDetails.c_str();
    jstring collj = m_jniEnv->NewStringUTF(coll_cstr);

    jobjectArray imgNamesj = (jobjectArray)m_jniEnv->NewObjectArray(
        num_imgs,
        m_jniEnv->FindClass("java/lang/String"),
        m_jniEnv->NewStringUTF(""));

    for (int i = 0; i < num_imgs; i++) {
        m_jniEnv->SetObjectArrayElement(
            imgNamesj, i, m_jniEnv->NewStringUTF(img_ptrs[i]));
    }

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addImageMethodID,
        type, ssize, tzj, size, md5j, sha1j, sha256j, devIdj, collj, imgNamesj);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }

    saveObjectInfo(objId, 0, TSK_DB_OBJECT_TYPE_IMG);
    return TSK_OK;
}

void
TskAutoDbJava::addAcquisitionDetails(int64_t imgId, const string& collectionDetails) {

    const char *coll_cstr = collectionDetails.c_str();
    jstring collj = m_jniEnv->NewStringUTF(coll_cstr);

    m_jniEnv->CallLongMethod(m_javaDbObj, m_addAcquisitionDetailsMethodID,
        imgId, collj);
}

/**
* Adds volume system to database. Object ID for new vs stored in objId.
*
* @param vs_info  Struct containing info for this volume system
* @param parObjId Parent object ID for the volume system
* @param objId    Object ID of new volume system
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addVsInfo(const TSK_VS_INFO* vs_info, int64_t parObjId, int64_t& objId) {

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addVolumeSystemMethodID,
        parObjId, vs_info->vstype, vs_info->offset, (uint64_t)vs_info->block_size);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }

    // Save the vs info to use for unallocated blocks later
    TSK_DB_VS_INFO vs_db;
    vs_db.objId = objId;
    vs_db.offset = vs_info->offset;
    vs_db.vstype = vs_info->vstype;
    vs_db.block_size = vs_info->block_size;
    m_savedVsInfo.push_back(vs_db);

    saveObjectInfo(objId, parObjId, TSK_DB_OBJECT_TYPE_VS);
    return TSK_OK;
}

/**
* Adds pool and pool volume system to database. Object ID for new pool vs stored in objId.
*
* @param pool_info  Struct containing info for this pool
* @param parObjId   Parent object ID for the pool
* @param objId      Object ID of new pool volume system
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addPoolInfoAndVS(const TSK_POOL_INFO *pool_info, int64_t parObjId, int64_t& objId) {

    // Add the pool
    jlong poolObjIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addPoolMethodID,
        parObjId, pool_info->ctype);
    int64_t poolObjId = (int64_t)poolObjIdj;

    if (poolObjId < 0) {
        return TSK_ERR;
    }
    saveObjectInfo(poolObjId, parObjId, TSK_DB_OBJECT_TYPE_POOL);

    // Add the pool volume
    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addVolumeSystemMethodID,
        poolObjIdj, TSK_VS_TYPE_APFS, pool_info->img_offset, (uint64_t)pool_info->block_size);
    objId = (int64_t)objIdj;

    saveObjectInfo(objId, poolObjId, TSK_DB_OBJECT_TYPE_VS);
    return TSK_OK;
}

/**
* Adds a pool volume to database. Object ID for new pool volume stored in objId.
*
* @param pool_vol  Struct containing info for this pool volume
* @param parObjId  Parent object ID
* @param objId     Object ID of new pool volume
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addPoolVolumeInfo(const TSK_POOL_VOLUME_INFO* pool_vol,
    int64_t parObjId, int64_t& objId) {

    jstring descj = m_jniEnv->NewStringUTF(pool_vol->desc);

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addVolumeMethodID,
        parObjId, (int64_t)pool_vol->index, pool_vol->block, pool_vol->num_blocks,
        descj, pool_vol->flags);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }

    saveObjectInfo(objId, parObjId, TSK_DB_OBJECT_TYPE_VOL);
    return TSK_OK;
}

/**
* Adds a volume to database. Object ID for new volume stored in objId.
*
* @param vs_part   Struct containing info for this volume
* @param parObjId  Parent object ID
* @param objId     Object ID of new volume
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addVolumeInfo(const TSK_VS_PART_INFO* vs_part,
    int64_t parObjId, int64_t& objId) {

    jstring descj = m_jniEnv->NewStringUTF(vs_part->desc);

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addVolumeMethodID,
        parObjId, (uint64_t)vs_part->addr, vs_part->start, vs_part->len,
        descj, vs_part->flags);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }

    // Save the volume info for creating unallocated blocks later
    TSK_DB_VS_PART_INFO vs_part_db;
    vs_part_db.objId = objId;
    vs_part_db.addr = vs_part->addr;
    vs_part_db.start = vs_part->start;
    vs_part_db.len = vs_part->len;
    strncpy(vs_part_db.desc, vs_part->desc, TSK_MAX_DB_VS_PART_INFO_DESC_LEN - 1);
    vs_part_db.flags = vs_part->flags;
    m_savedVsPartInfo.push_back(vs_part_db);

    saveObjectInfo(objId, parObjId, TSK_DB_OBJECT_TYPE_VOL);
    return TSK_OK;
}

/**
* Adds a file system to database. Object ID for new file system stored in objId.
*
* @param fs_info   Struct containing info for this file system
* @param parObjId  Parent object ID
* @param objId     Object ID of new file system
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addFsInfo(const TSK_FS_INFO* fs_info, int64_t parObjId,
    int64_t& objId) {

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addFileSystemMethodID,
        parObjId, fs_info->offset, (int)fs_info->ftype, (uint64_t)fs_info->block_size,
        fs_info->block_count, fs_info->root_inum, fs_info->first_inum,
        fs_info->last_inum);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }

    // Save the file system info for creating unallocated blocks later
    TSK_DB_FS_INFO fs_info_db;
    fs_info_db.objId = objId;
    fs_info_db.imgOffset = fs_info->offset;
    fs_info_db.fType = fs_info->ftype;
    fs_info_db.block_size = fs_info->block_size;
    fs_info_db.block_count = fs_info->block_count;
    fs_info_db.root_inum = fs_info->root_inum;
    fs_info_db.first_inum = fs_info->first_inum;
    fs_info_db.last_inum = fs_info->last_inum;
    m_savedFsInfo.push_back(fs_info_db);

    saveObjectInfo(objId, parObjId, TSK_DB_OBJECT_TYPE_FS);
    return TSK_OK;
}

/**
* Adds a file to database. Object ID for new file stored in objId.
*
* @param fs_file
* @param fs_attr
* @param path      File path
* @param parObjId  Parent object ID
* @param fsObjId   Object ID of the file system
* @param objId     Object ID of new file
* @param dataSourceObjId  Object ID of the data source
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addFsFile(TSK_FS_FILE* fs_file,
    const TSK_FS_ATTR* fs_attr, const char* path,
    int64_t fsObjId, int64_t& objId, int64_t dataSourceObjId) {

    if (fs_file->name == NULL)
        return TSK_ERR;

    // The object id for the parent folder. Will stay as zero if not the root folder
    int64_t parObjId = 0;

    // Root directory's parent should be the file system object.
    // Make sure it doesn't have a name, so that we don't pick up ".." entries
    if ((fs_file->fs_info->root_inum == fs_file->name->meta_addr) &&
        ((fs_file->name->name == NULL) || (strlen(fs_file->name->name) == 0))) {
        // File is in the root directory
        parObjId = fsObjId;
    }

    // Add the file to the database
    return addFile(fs_file, fs_attr, path, fsObjId, parObjId, dataSourceObjId);
}

/**
* Extract the extension from the given file name and store it in the supplied string.
* @param name A file name
* @param extension The file name extension will be extracted to extension.
*/
void extractExtension(char *name, char *extension) {
    char *ext = strrchr(name, '.');

    //if ext is not null and is not the entire filename...
    if (ext && (name != ext)) {
        size_t extLen = strlen(ext);
        //... and doesn't only contain the '.' and isn't too long to be a real extension.
        if ((1 < extLen) && (extLen < 15)) {
            strncpy(extension, ext + 1, extLen - 1);
            //normalize to lower case, only works for ascii
            for (int i = 0; extension[i]; i++) {
                extension[i] = tolower(extension[i]);
            }
        }
    }
}

/**
* Convert a sequence of characters to a jstring object.
* We first convert the character sequence to UTF16 and then
* use the JNI NewString() method to create the jstring.
* We do it this way because we encountered data that contained
* 4 byte (or more) UTF8 encoded characters and the JNI NewStringUTF()
* method does not handle 4 byte UTF8 encoding.
*
* @param input The sequence of characters to be turned into a jstring.
* @param newJString The new jstring object created from the input.
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM TskAutoDbJava::createJString(const char * input, jstring & newJString) {
    size_t input_len = strlen(input) + 1;
    UTF16 * utf16_input;

    if ((utf16_input = (UTF16 *)tsk_malloc(input_len * sizeof(UTF16))) == NULL) {
        return TSK_ERR;
    }

    UTF8 * source = (UTF8 *)input;
    UTF16 * target = utf16_input;

    if (tsk_UTF8toUTF16((const UTF8 **)&source, (const UTF8 *)&source[input_len], &target, &target[input_len], TSKlenientConversion) != TSKconversionOK) {
        free(utf16_input);
        return TSK_ERR;
    }

    /*
     * To determine the length of the new string we we subtract the address
     * of the start of the UTF16 buffer from the address at the end of the 
     * UTF16 buffer (target is advanced in the call to the conversion routine
     * above).
     */
    newJString = m_jniEnv->NewString(utf16_input, (target - utf16_input) - 1);

    free(utf16_input);
    return TSK_OK;
}

/**
* Adds a file and its associated slack file to database.
* Does not learn object ID for new files, and files may
* not be added to the database immediately.
*
* @param fs_file
* @param fs_attr
* @param path      File path
* @param fsObjId   Object ID of the file system
* @param parObjId  Parent object ID if known, 0 otherwise
* @param dataSourceObjId  Object ID of the data source
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addFile(TSK_FS_FILE* fs_file,
    const TSK_FS_ATTR* fs_attr, const char* path,
    int64_t fsObjId, int64_t parObjId,
    int64_t dataSourceObjId)
{
    time_t mtime = 0;
    time_t crtime = 0;
    time_t ctime = 0;
    time_t atime = 0;
    TSK_OFF_T size = 0;
    int meta_type = 0;
    int meta_flags = 0;
    int meta_mode = 0;
    int meta_seq = 0;
    int gid = 0;
    int uid = 0;
    int type = TSK_FS_ATTR_TYPE_NOT_FOUND;
    int idx = 0;

    if (fs_file->name == NULL)
        return TSK_OK;

    if (fs_file->meta) {
        mtime = fs_file->meta->mtime;
        atime = fs_file->meta->atime;
        ctime = fs_file->meta->ctime;
        crtime = fs_file->meta->crtime;
        meta_type = fs_file->meta->type;
        meta_flags = fs_file->meta->flags;
        meta_mode = fs_file->meta->mode;
        gid = fs_file->meta->gid;
        uid = fs_file->meta->uid;
        meta_seq = fs_file->meta->seq;
    }

    size_t attr_nlen = 0;
    if (fs_attr) {
        type = fs_attr->type;
        idx = fs_attr->id;
        size = fs_attr->size;
        if (fs_attr->name) {
            if ((fs_attr->type != TSK_FS_ATTR_TYPE_NTFS_IDXROOT) ||
                (strcmp(fs_attr->name, "$I30") != 0)) {
                attr_nlen = strlen(fs_attr->name);
            }
        }
    }

    // sanity check
    if (size < 0) {
        size = 0;
    }

    // combine name and attribute name
    size_t len = strlen(fs_file->name->name);
    char * name;
    size_t nlen = len + attr_nlen + 11; // Extra space for possible colon and '-slack'
    if ((name = (char *)tsk_malloc(nlen)) == NULL) {
        return TSK_ERR;
    }

    strncpy(name, fs_file->name->name, nlen);

    char extension[24] = "";
    extractExtension(name, extension);

    // Add the attribute name
    if (attr_nlen > 0) {
        strncat(name, ":", nlen - strlen(name));
        if (fs_attr != NULL) {
            strncat(name, fs_attr->name, nlen - strlen(name));
        }
    }

    jstring namej;
    if (createJString(name, namej) != TSK_OK) {
        free(name);
        return TSK_ERR;
    }

    // clean up path
    // +2 = space for leading slash and terminating null
    size_t path_len = strlen(path) + 2;
    char* escaped_path;
    if ((escaped_path = (char *)tsk_malloc(path_len)) == NULL) {
        free(name);
        return TSK_ERR;
    }
    strncpy(escaped_path, "/", path_len);
    strncat(escaped_path, path, path_len - strlen(escaped_path));

    jstring pathj;
    if (createJString(escaped_path, pathj) != TSK_OK) {
        free(name);
        free(escaped_path);
        return TSK_ERR;
    }

    // Escaped path is not needed beyond this point so free it.
    free(escaped_path);

    jstring extj;
    if (createJString(extension, extj) != TSK_OK) {
        free(name);
        return TSK_ERR;
    }

    /* NTFS uses sequence, otherwise we hash the path. We do this to map to the
    * correct parent folder if there are two from the root dir that eventually point to
    * the same folder (one deleted and one allocated) or two hard links. */
    jlong par_seqj;
    if (TSK_FS_TYPE_ISNTFS(fs_file->fs_info->ftype))
    {
        par_seqj = fs_file->name->par_seq;
    }
    else {
        par_seqj = -1;
    }
    TSK_INUM_T par_meta_addr = fs_file->name->par_addr;
 
    // Add the file to the database
    jlong ret_val = m_jniEnv->CallLongMethod(m_javaDbObj, m_addFileMethodID,
        parObjId, fsObjId,
        dataSourceObjId,
        TSK_DB_FILES_TYPE_FS,
        type, idx, namej,
        fs_file->name->meta_addr, (uint64_t)fs_file->name->meta_seq,
        fs_file->name->type, meta_type, fs_file->name->flags, meta_flags,
        size,
        (unsigned long long)crtime, (unsigned long long)ctime, (unsigned long long) atime, (unsigned long long) mtime,
        meta_mode, gid, uid, 
        pathj, extj, 
        (uint64_t)meta_seq, par_meta_addr, par_seqj);

    if (ret_val < 0) {
        free(name);
        return TSK_ERR;
    }

    // Add entry for the slack space.
    // Current conditions for creating a slack file:
    //   - File name is not empty, "." or ".."
    //   - Data is non-resident
    //   - The allocated size is greater than the initialized file size
    //     See github issue #756 on why initsize and not size.
    //   - The data is not compressed
    if ((fs_attr != NULL)
        && ((strlen(name) > 0) && (!TSK_FS_ISDOT(name)))
        && (!(fs_file->meta->flags & TSK_FS_META_FLAG_COMP))
        && (fs_attr->flags & TSK_FS_ATTR_NONRES)
        && (fs_attr->nrd.allocsize > fs_attr->nrd.initsize)) {
        strncat(name, "-slack", 6);
        if (strlen(extension) > 0) {
            strncat(extension, "-slack", 6);
        }
        jstring slackNamej;
        if (createJString(name, slackNamej) != TSK_OK) {
            free(name);
            return TSK_ERR;
        }
        jstring slackExtj;
        if (createJString(extension, slackExtj) != TSK_OK) {
            free(name);
            return TSK_ERR;
        }
        TSK_OFF_T slackSize = fs_attr->nrd.allocsize - fs_attr->nrd.initsize;

        // Add slack file to database
        jlong ret_val = m_jniEnv->CallLongMethod(m_javaDbObj, m_addFileMethodID,
            parObjId, fsObjId,
            dataSourceObjId,
            TSK_DB_FILES_TYPE_SLACK,
            type, idx, slackNamej,
            fs_file->name->meta_addr, (uint64_t)fs_file->name->meta_seq,
            TSK_FS_NAME_TYPE_REG, TSK_FS_META_TYPE_REG, fs_file->name->flags, meta_flags,
            slackSize,
            (unsigned long long)crtime, (unsigned long long)ctime, (unsigned long long) atime, (unsigned long long) mtime,
            meta_mode, gid, uid, // md5TextPtr, known,
            pathj, slackExtj, 
            (uint64_t)meta_seq, par_meta_addr, par_seqj);

        if (ret_val < 0) {
            free(name);
            return TSK_ERR;
        }
    }

    free(name);

    return TSK_OK;
}

// Internal function object to check for range overlap
typedef struct _checkFileLayoutRangeOverlap {
    const vector<TSK_DB_FILE_LAYOUT_RANGE> & ranges;
    bool hasOverlap;

    explicit _checkFileLayoutRangeOverlap(const vector<TSK_DB_FILE_LAYOUT_RANGE> & ranges)
        : ranges(ranges), hasOverlap(false) {}

    bool getHasOverlap() const { return hasOverlap; }
    void operator() (const TSK_DB_FILE_LAYOUT_RANGE & range) {
        if (hasOverlap)
            return; //no need to check other

        uint64_t start = range.byteStart;
        uint64_t end = start + range.byteLen;

        vector<TSK_DB_FILE_LAYOUT_RANGE>::const_iterator it;
        for (it = ranges.begin(); it != ranges.end(); ++it) {
            const TSK_DB_FILE_LAYOUT_RANGE * otherRange = &(*it);
            if (&range == otherRange)
                continue; //skip, it's the same range
            uint64_t otherStart = otherRange->byteStart;
            uint64_t otherEnd = otherStart + otherRange->byteLen;
            if (start <= otherEnd && end >= otherStart) {
                hasOverlap = true;
                break;
            }
        }
    }

} checkFileLayoutRangeOverlap;

/**
* Internal helper method to add unalloc, unused and carved files with layout ranges to db
* Generates file_name and populates tsk_files, tsk_objects and tsk_file_layout tables
* Adds a single entry to tsk_files table with an auto-generated file name, tsk_objects table, and one or more entries to tsk_file_layout table
* @param dbFileType  Type of file
* @param parentObjId Id of the parent object in the database (fs, volume, or image)
* @param fsObjId     parent fs, or NULL if the file is not associated with fs
* @param size        Number of bytes in file
* @param ranges      vector containing one or more TSK_DB_FILE_LAYOUT_RANGE layout ranges (in)
* @param objId       object id of the file object created (output)
* @param dataSourceObjId  The object ID for the data source
* @returns TSK_OK on success or TSK_ERR on error.
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addFileWithLayoutRange(const TSK_DB_FILES_TYPE_ENUM dbFileType, const int64_t parentObjId,
    const int64_t fsObjId, const uint64_t size,
    vector<TSK_DB_FILE_LAYOUT_RANGE>& ranges, int64_t& objId,
    int64_t dataSourceObjId) {

    const size_t numRanges = ranges.size();

    if (numRanges < 1) {
        tsk_error_reset();
        tsk_error_set_errno(TSK_ERR_AUTO_DB);
        tsk_error_set_errstr("Error addFileWithLayoutRange() - no ranges present");
        return TSK_ERR;
    }

    stringstream fileNameSs;
    switch (dbFileType) {
    case TSK_DB_FILES_TYPE_UNALLOC_BLOCKS:
        fileNameSs << "Unalloc";
        break;

    case TSK_DB_FILES_TYPE_UNUSED_BLOCKS:
        fileNameSs << "Unused";
        break;

    case TSK_DB_FILES_TYPE_CARVED:
        fileNameSs << "Carved";
        break;
    default:
        stringstream sserr;
        tsk_error_reset();
        tsk_error_set_errno(TSK_ERR_AUTO_DB);
        sserr << "Error addFileWithLayoutRange() - unsupported file type for file layout range: ";
        sserr << (int)dbFileType;
        tsk_error_set_errstr("%s", sserr.str().c_str());
        return TSK_ERR;
    }

    //ensure layout ranges are sorted (to generate file name and to be inserted in sequence order)
    sort(ranges.begin(), ranges.end());

    //dome some checking
    //ensure there is no overlap and each range has unique byte range
    const checkFileLayoutRangeOverlap & overlapRes =
        for_each(ranges.begin(), ranges.end(), checkFileLayoutRangeOverlap(ranges));
    if (overlapRes.getHasOverlap()) {
        tsk_error_reset();
        tsk_error_set_errno(TSK_ERR_AUTO_DB);
        tsk_error_set_errstr("Error addFileWithLayoutRange() - overlap detected between ranges");
        return TSK_ERR;
    }

    //construct filename with parent obj id, start byte of first range, end byte of last range
    fileNameSs << "_" << parentObjId << "_" << ranges[0].byteStart;
    fileNameSs << "_" << (ranges[numRanges - 1].byteStart + ranges[numRanges - 1].byteLen);

    jstring namej = m_jniEnv->NewStringUTF(fileNameSs.str().c_str());

    // Insert into tsk files and tsk objects
    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addLayoutFileMethodID,
        parentObjId, fsObjId, dataSourceObjId, dbFileType, namej, size);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }

    // Fill in fileObjId and insert ranges
    for (vector<TSK_DB_FILE_LAYOUT_RANGE>::iterator it = ranges.begin();
        it != ranges.end(); ++it) {
        TSK_DB_FILE_LAYOUT_RANGE & range = *it;
        range.fileObjId = objId;
        if (-1 == m_jniEnv->CallLongMethod(m_javaDbObj, m_addLayoutFileRangeMethodID,
            objId, range.byteStart, range.byteLen, (uint64_t)range.sequence)) {
            return TSK_ERR;
        }
    }

    return TSK_OK;
}

/**
* Adds information about a unallocated file with layout ranges into the database.
* Adds a single entry to tsk_files table with an auto-generated file name, tsk_objects table, and one or more entries to tsk_file_layout table
* @param parentObjId Id of the parent object in the database (fs, volume, or image)
* @param fsObjId parent fs, or NULL if the file is not associated with fs
* @param size Number of bytes in file
* @param ranges vector containing one or more TSK_DB_FILE_LAYOUT_RANGE layout ranges (in)
* @param objId object id of the file object created (output)
* @param dataSourceObjId The object ID for the data source
* @returns TSK_OK on success or TSK_ERR on error.
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addUnallocBlockFile(const int64_t parentObjId, const int64_t fsObjId, const uint64_t size,
    vector<TSK_DB_FILE_LAYOUT_RANGE>& ranges, int64_t& objId,
    int64_t dataSourceObjId) {
    return addFileWithLayoutRange(TSK_DB_FILES_TYPE_UNALLOC_BLOCKS, parentObjId, fsObjId, size, ranges, objId,
        dataSourceObjId);
}

/**
* Adds information about a unused file with layout ranges into the database.
* Adds a single entry to tsk_files table with an auto-generated file name, tsk_objects table, and one or more entries to tsk_file_layout table
* @param parentObjId Id of the parent object in the database (fs, volume, or image)
* @param fsObjId parent fs, or NULL if the file is not associated with fs
* @param size Number of bytes in file
* @param ranges vector containing one or more TSK_DB_FILE_LAYOUT_RANGE layout ranges (in)
* @param objId object id of the file object created (output)
* @param dataSourceObjId The object ID for the data source
* @returns TSK_OK on success or TSK_ERR on error.
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addUnusedBlockFile(const int64_t parentObjId, const int64_t fsObjId, const uint64_t size,
    vector<TSK_DB_FILE_LAYOUT_RANGE>& ranges, int64_t& objId,
    int64_t dataSourceObjId) {
    return addFileWithLayoutRange(TSK_DB_FILES_TYPE_UNUSED_BLOCKS, parentObjId, fsObjId, size, ranges, objId,
        dataSourceObjId);
}



/**
* Add a virtual dir to hold unallocated block files for this file system.
* @param fsObjId  Object ID of the file system
* @param objId    Object ID of the created virtual dir
* @param dataSourceObjId  Object ID of the data source
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addUnallocFsBlockFilesParent(const int64_t fsObjId, int64_t& objId,
    int64_t dataSourceObjId) {

    const char * const unallocDirName = "$Unalloc";
    jstring namej = m_jniEnv->NewStringUTF(unallocDirName);

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addUnallocParentMethodID,
        fsObjId, namej);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }
    return TSK_OK;
}

/**
* Adds a new volume that will hold the unallocated blocks for the pool.
*
* @param vol_index The index for the new volume (should be one higher than the number of pool volumes)
* @param parObjId  The object ID of the parent volume system
* @param objId     Will be set to the object ID of the new volume
*
* @returns TSK_ERR on error, TSK_OK on success
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addUnallocatedPoolVolume(int vol_index, int64_t parObjId, int64_t& objId)
{
    const char *desc = "Unallocated Blocks";
    jstring descj = m_jniEnv->NewStringUTF(desc);

    jlong objIdj = m_jniEnv->CallLongMethod(m_javaDbObj, m_addVolumeMethodID,
        parObjId, vol_index, 0, 0,
        descj, 0);
    objId = (int64_t)objIdj;

    if (objId < 0) {
        return TSK_ERR;
    }
    return TSK_OK;
}

void TskAutoDbJava::close() {
    if (m_jniEnv == NULL) {
        return;
    }

    if (m_javaDbObj != NULL) {
        m_jniEnv->DeleteGlobalRef(m_javaDbObj);
    }

    if (m_callbackClass != NULL) {
        m_jniEnv->DeleteGlobalRef(m_callbackClass);
    }
}

int64_t TskAutoDbJava::getImageID() {
    return m_curImgId;
}

void TskAutoDbJava::closeImage() {
    TskAuto::closeImage();
}

void TskAutoDbJava::setAddFileSystems(bool addFileSystems) {
    m_addFileSystems = addFileSystems;
}

void TskAutoDbJava::setNoFatFsOrphans(bool noFatFsOrphans) {
    m_noFatFsOrphans = noFatFsOrphans;
}

void TskAutoDbJava::setAddUnallocSpace(bool addUnallocSpace) {
    setAddUnallocSpace(addUnallocSpace, -1);
}

void TskAutoDbJava::setAddUnallocSpace(bool addUnallocSpace, int64_t minChunkSize) {
    m_addUnallocSpace = addUnallocSpace;
    m_minChunkSize = minChunkSize;
    m_maxChunkSize = -1;
}

void TskAutoDbJava::setAddUnallocSpace(int64_t minChunkSize, int64_t maxChunkSize) {
    m_addUnallocSpace = true;
    m_minChunkSize = minChunkSize;
    m_maxChunkSize = maxChunkSize;
}

/**
 * Adds an image to the database.
 *
 * @param a_num Number of image parts
 * @param a_images Array of paths to the image parts
 * @param a_type Image type
 * @param a_ssize Size of device sector in bytes (or 0 for default)
 * @param a_deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID).
 * @return 0 for success, 1 for failure
 */
uint8_t
    TskAutoDbJava::openImageUtf8(int a_num, const char *const a_images[],
    TSK_IMG_TYPE_ENUM a_type, unsigned int a_ssize, const char* a_deviceId)
{
    uint8_t retval =
        TskAuto::openImageUtf8(a_num, a_images, a_type, a_ssize);
    if (retval != 0) {
        return retval;
    }

    if (addImageDetails(a_deviceId)) {
        return 1;
    }
    return 0;
}

/**
 * Adds an image to the database.
 *
 * @param a_num Number of image parts
 * @param a_images Array of paths to the image parts
 * @param a_type Image type
 * @param a_ssize Size of device sector in bytes (or 0 for default)
 * @param a_deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID).
 * @return 0 for success, 1 for failure
 */
uint8_t
    TskAutoDbJava::openImage(int a_num, const TSK_TCHAR * const a_images[],
    TSK_IMG_TYPE_ENUM a_type, unsigned int a_ssize, const char* a_deviceId)
{

#ifdef TSK_WIN32

    uint8_t retval = TskAuto::openImage(a_num, a_images, a_type, a_ssize);

    if (retval != 0) {
        return retval;
    }

    return (addImageDetails(a_deviceId));
#else
    return openImageUtf8(a_num, a_images, a_type, a_ssize, a_deviceId);
#endif
}

/**
* Adds an image to the database. Requires that m_img_info is already initialized
*
* @param a_deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID).
* @return 0 for success, 1 for failure
*/
uint8_t
TskAutoDbJava::openImage(const char* a_deviceId)
{
    if (m_img_info == NULL) {
        return 1;
    }

    return(addImageDetails(a_deviceId));
}

/**
 * Adds image details to the existing database tables.
 *
 * @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID).
 * @return Returns 0 for success, 1 for failure
 */
uint8_t
TskAutoDbJava::addImageDetails(const char* deviceId)
{
   string md5 = "";
   string sha1 = "";
   string collectionDetails = "";
#if HAVE_LIBEWF 
   if (m_img_info->itype == TSK_IMG_TYPE_EWF_EWF) {
     // @@@ This should really probably be inside of a tsk_img_ method
       IMG_EWF_INFO *ewf_info = (IMG_EWF_INFO *)m_img_info;
       if (ewf_info->md5hash_isset) {
           md5 = ewf_info->md5hash;
       }
       if (ewf_info->sha1hash_isset) {
           sha1 = ewf_info->sha1hash;
       }

       collectionDetails = ewf_get_details(ewf_info);   
   }
#endif

    // If the image has already been added to the database, update the acquisition details and return.
    if (m_curImgId > 0) {
        addAcquisitionDetails(m_curImgId, collectionDetails);
        return 0;
    }

    string devId;
    if (NULL != deviceId) {
        devId = deviceId; 
    } else {
        devId = "";
    }

    char **img_ptrs;
#ifdef TSK_WIN32
    // convert image paths to UTF-8
    img_ptrs = (char **)tsk_malloc(m_img_info->num_img * sizeof(char *));
    if (img_ptrs == NULL) {
        return 1;
    }

    for (int i = 0; i < m_img_info->num_img; i++) {
        char * img2 = (char*)tsk_malloc(1024 * sizeof(char));
        UTF8 *ptr8;
        UTF16 *ptr16;

        ptr8 = (UTF8 *)img2;
        ptr16 = (UTF16 *)m_img_info->images[i];

        uint8_t retval =
            tsk_UTF16toUTF8_lclorder((const UTF16 **)&ptr16, (UTF16 *)
                & ptr16[TSTRLEN(m_img_info->images[i]) + 1], &ptr8,
                (UTF8 *)((uintptr_t)ptr8 + 1024), TSKlenientConversion);
        if (retval != TSKconversionOK) {
            tsk_error_reset();
            tsk_error_set_errno(TSK_ERR_AUTO_UNICODE);
            tsk_error_set_errstr("Error converting image to UTF-8\n");
            return 1;
        }
        img_ptrs[i] = img2;
    }
#else 
    img_ptrs = m_img_info->images;
#endif


    if (TSK_OK != addImageInfo(m_img_info->itype, m_img_info->sector_size,
        m_curImgId, m_curImgTZone, m_img_info->size, md5, sha1, "", devId, collectionDetails,
        img_ptrs, m_img_info->num_img)) {
        registerError();
        return 1;
    }

#ifdef TSK_WIN32
    //cleanup
    for (int i = 0; i < m_img_info->num_img; ++i) {
        free(img_ptrs[i]);
    }
    free(img_ptrs);
#endif

    return 0;
}


TSK_FILTER_ENUM 
TskAutoDbJava::filterVs(const TSK_VS_INFO * vs_info)
{
    m_vsFound = true;
    if (TSK_OK != addVsInfo(vs_info, m_curImgId, m_curVsId)) {
        registerError();
        return TSK_FILTER_STOP;
    }

    return TSK_FILTER_CONT;
}

TSK_FILTER_ENUM
TskAutoDbJava::filterPool(const TSK_POOL_INFO * pool_info)
{
    m_poolFound = true;

    if (m_volFound && m_vsFound) {
        // there's a volume system and volume
        if (TSK_OK != addPoolInfoAndVS(pool_info, m_curVolId, m_curPoolVs)) {
            registerError();
            return TSK_FILTER_STOP;
        }
        // Save the parent obj ID for the pool
        m_poolOffsetToParentId[pool_info->img_offset] = m_curVolId;
    }
    else {
        // pool doesn't live in a volume, use image as parent
        if (TSK_OK != addPoolInfoAndVS(pool_info, m_curImgId, m_curPoolVs)) {
            registerError();
            return TSK_FILTER_STOP;
        }
        // Save the parent obj ID for the pool
        m_poolOffsetToParentId[pool_info->img_offset] = m_curImgId;
    }

    // Store the volume system object ID for later use
    m_poolOffsetToVsId[pool_info->img_offset] = m_curPoolVs;

    return TSK_FILTER_CONT;
}

/**
* Adds unallocated pool blocks to a new volume.
*
* @param numPool Will be updated with the number of pools processed
*
* @return Returns 0 for success, 1 for failure
*/
TSK_RETVAL_ENUM
TskAutoDbJava::addUnallocatedPoolBlocksToDb(size_t & numPool) {

    for (size_t i = 0; i < m_poolInfos.size(); i++) {
        const TSK_POOL_INFO * pool_info = m_poolInfos[i];
        if (m_poolOffsetToVsId.find(pool_info->img_offset) == m_poolOffsetToVsId.end()) {
            tsk_error_reset();
            tsk_error_set_errno(TSK_ERR_AUTO_DB);
            tsk_error_set_errstr("Error addUnallocatedPoolBlocksToDb() - could not find volume system object ID for pool at offset %lld", pool_info->img_offset);
            return TSK_ERR;
        }
        int64_t curPoolVs = m_poolOffsetToVsId[pool_info->img_offset];

        /* Make sure  the pool_info is still allocated */
        if (pool_info->tag != TSK_POOL_INFO_TAG) {
            tsk_error_reset();
            tsk_error_set_errno(TSK_ERR_AUTO_DB);
            tsk_error_set_errstr("Error addUnallocatedPoolBlocksToDb() - pool_info is not allocated");
            return TSK_ERR;
        }

        /* Only APFS pools are currently supported */
        if (pool_info->ctype != TSK_POOL_TYPE_APFS) {
            continue;
        }

        /* Increment the count of pools found */
        numPool++;

        /* Create the volume */
        int64_t unallocVolObjId;
        if (TSK_ERR == addUnallocatedPoolVolume(pool_info->num_vols, curPoolVs, unallocVolObjId)) {
            tsk_error_reset();
            tsk_error_set_errno(TSK_ERR_AUTO_DB);
            tsk_error_set_errstr("Error addUnallocatedPoolBlocksToDb() - error createing unallocated space pool volume");
            return TSK_ERR;
        }

        /* Create the unallocated space files */
        TSK_FS_ATTR_RUN * unalloc_runs = tsk_pool_unallocated_runs(pool_info);
        TSK_FS_ATTR_RUN * current_run = unalloc_runs;
        vector<TSK_DB_FILE_LAYOUT_RANGE> ranges;
        while (current_run != NULL) {

            TSK_DB_FILE_LAYOUT_RANGE tempRange(current_run->addr * pool_info->block_size, current_run->len * pool_info->block_size, 0);

            ranges.push_back(tempRange);
            int64_t fileObjId = 0;
            if (TSK_ERR == addUnallocBlockFile(unallocVolObjId, 0, current_run->len * pool_info->block_size, ranges, fileObjId, m_curImgId)) {
                registerError();
                tsk_fs_attr_run_free(unalloc_runs);
                return TSK_ERR;
            }

            current_run = current_run->next;
            ranges.clear();
        }
        tsk_fs_attr_run_free(unalloc_runs);
    }

    return TSK_OK;
}

TSK_FILTER_ENUM
TskAutoDbJava::filterPoolVol(const TSK_POOL_VOLUME_INFO * pool_vol)
{

    if (TSK_OK != addPoolVolumeInfo(pool_vol, m_curPoolVs, m_curPoolVol)) {
        registerError();
        return TSK_FILTER_STOP;
    }

    return TSK_FILTER_CONT;
}

TSK_FILTER_ENUM
TskAutoDbJava::filterVol(const TSK_VS_PART_INFO * vs_part)
{
    m_volFound = true;
    m_foundStructure = true;
    m_poolFound = false;

    if (TSK_OK != addVolumeInfo(vs_part, m_curVsId, m_curVolId)) {
        registerError();
        return TSK_FILTER_STOP;
    }

    return TSK_FILTER_CONT;
}


TSK_FILTER_ENUM
TskAutoDbJava::filterFs(TSK_FS_INFO * fs_info)
{
    TSK_FS_FILE *file_root;
    m_foundStructure = true;

    if (m_poolFound) {
        // there's a pool
        if (TSK_OK != addFsInfo(fs_info, m_curPoolVol, m_curFsId)) {
            registerError();
            return TSK_FILTER_STOP;
        }
    }
    else if (m_volFound && m_vsFound) {
        // there's a volume system and volume
        if (TSK_OK != addFsInfo(fs_info, m_curVolId, m_curFsId)) {
            registerError();
            return TSK_FILTER_STOP;
        }
    }
    else {
        // file system doesn't live in a volume, use image as parent
        if (TSK_OK != addFsInfo(fs_info, m_curImgId, m_curFsId)) {
            registerError();
            return TSK_FILTER_STOP;
        }
    }


    // We won't hit the root directory on the walk, so open it now 
    if ((file_root = tsk_fs_file_open(fs_info, NULL, "/")) != NULL) {
        processFile(file_root, "");
        tsk_fs_file_close(file_root);
        file_root = NULL;
    }


    // make sure that flags are set to get all files -- we need this to
    // find parent directory
     
    TSK_FS_DIR_WALK_FLAG_ENUM filterFlags = (TSK_FS_DIR_WALK_FLAG_ENUM)
        (TSK_FS_DIR_WALK_FLAG_ALLOC | TSK_FS_DIR_WALK_FLAG_UNALLOC);

    //check if to skip processing of FAT orphans
    if (m_noFatFsOrphans 
        && TSK_FS_TYPE_ISFAT(fs_info->ftype) ) {
            filterFlags = (TSK_FS_DIR_WALK_FLAG_ENUM) (filterFlags | TSK_FS_DIR_WALK_FLAG_NOORPHAN);
    }

    setFileFilterFlags(filterFlags);

    return TSK_FILTER_CONT;
}

/* Insert the file data into the file table.
 * @param fs_file
 * @param fs_attr
 * @param path
 * Returns TSK_ERR on error.
 */
TSK_RETVAL_ENUM
    TskAutoDbJava::insertFileData(TSK_FS_FILE * fs_file,
    const TSK_FS_ATTR * fs_attr, const char *path)
{
    if (TSK_ERR == addFsFile(fs_file, fs_attr, path, m_curFsId, m_curFileId,
            m_curImgId)) {
        registerError();
        return TSK_ERR;
    }

    return TSK_OK;
}

/**
 * Analyzes the open image and adds image info to a database.
 * Does not deal with transactions and such.  Refer to startAddImage()
 * for more control. 
 * @returns 1 if a critical error occurred (DB doesn't exist, no file system, etc.), 2 if errors occurred at some point adding files to the DB (corrupt file, etc.), and 0 otherwise.  Errors will have been registered.
 */
uint8_t TskAutoDbJava::addFilesInImgToDb()
{
    // @@@ This seems bad because we are overriding what the user may
    // have set. We should remove the public API if we are going to 
    // override it -- presumably this was added so that we always have
    // unallocated volume space...
    setVolFilterFlags((TSK_VS_PART_FLAG_ENUM) (TSK_VS_PART_FLAG_ALLOC |
            TSK_VS_PART_FLAG_UNALLOC));

    uint8_t retVal = 0;
    if (findFilesInImg()) {
        // map the boolean return value from findFiles to the three-state return value we use
        // @@@ findFiles should probably return this three-state enum too
        if (m_foundStructure == false) {
            retVal = 1;
        }
        else {
            retVal = 2;
        }
    }

    TSK_RETVAL_ENUM addUnallocRetval = TSK_OK;
    if (m_addUnallocSpace)
        addUnallocRetval = addUnallocSpaceToDb();

    // findFiles return value trumps unalloc since it can return either 2 or 1.
    if (retVal) {
        return retVal;
    }
    else if (addUnallocRetval == TSK_ERR) {
        return 2;
    }
    else {
        return 0;
    }
}


/**
 * Start the process to add image/file metadata to database inside of a transaction. 
 * User must call either commitAddImage() to commit the changes,
 * or revertAddImage() to revert them.
 *
 * @param numImg Number of image parts
 * @param imagePaths Array of paths to the image parts
 * @param imgType Image type
 * @param sSize Size of device sector in bytes (or 0 for default)
 * @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID)
 * @return 0 for success, 1 for failure
 */
uint8_t
    TskAutoDbJava::startAddImage(int numImg, const TSK_TCHAR * const imagePaths[],
    TSK_IMG_TYPE_ENUM imgType, unsigned int sSize, const char* deviceId)
{
    if (tsk_verbose)
        tsk_fprintf(stderr, "TskAutoDbJava::startAddImage: Starting add image process\n");

    if (openImage(numImg, imagePaths, imgType, sSize, deviceId)) {
        tsk_error_set_errstr2("TskAutoDbJava::startAddImage");
        registerError();
        return 1;
    }

    if (m_imageWriterEnabled) {
        tsk_img_writer_create(m_img_info, m_imageWriterPath);
    }
    
    if (m_addFileSystems) {
        return addFilesInImgToDb();
    } else {
        return 0;
    }
}

/**
* Start the process to add image/file metadata to database inside of a transaction.
* User must call either commitAddImage() to commit the changes,
* or revertAddImage() to revert them.
*
* @param img_info Previously initialized TSK_IMG_INFO object
* @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID)
* @return 0 for success, 1 for failure
*/
uint8_t
TskAutoDbJava::startAddImage(TSK_IMG_INFO * img_info, const char* deviceId)
{
    openImageHandle(img_info);

    if (m_img_info == NULL) {
        return 1;
    }

    if (tsk_verbose)
        tsk_fprintf(stderr, "TskAutoDbJava::startAddImage: Starting add image process\n");

    if (openImage(deviceId)) {
        tsk_error_set_errstr2("TskAutoDbJava::startAddImage");
        registerError();
        return 1;
    }

    if (m_imageWriterEnabled) {
        if (tsk_img_writer_create(m_img_info, m_imageWriterPath)) {
            registerError();
            return 1;
        }
    }

    if (m_addFileSystems) {
        return addFilesInImgToDb();
    }
    else {
        return 0;
    }
}


#ifdef WIN32
/**
 * Start the process to add image/file metadata to database inside of a transaction. 
 * Same functionality as addFilesInImgToDb().  Reverts
 * all changes on error. User must call either commitAddImage() to commit the changes,
 * or revertAddImage() to revert them.
 *
 * @param numImg Number of image parts
 * @param imagePaths Array of paths to the image parts
 * @param imgType Image type
 * @param sSize Size of device sector in bytes (or 0 for default)
 * @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID)
 * @return 0 for success 1, for failure
 */
uint8_t
    TskAutoDbJava::startAddImage(int numImg, const char *const imagePaths[],
    TSK_IMG_TYPE_ENUM imgType, unsigned int sSize, const char* deviceId)
{
    if (tsk_verbose) 
        tsk_fprintf(stderr, "TskAutoDbJava::startAddImage_utf8: Starting add image process\n");

    if (openImageUtf8(numImg, imagePaths, imgType, sSize, deviceId)) {
        tsk_error_set_errstr2("TskAutoDbJava::startAddImage");
        registerError();
        return 1;
    }
    if (m_imageWriterEnabled) {
        tsk_img_writer_create(m_img_info, m_imageWriterPath);
    }

    if (m_addFileSystems) {
        return addFilesInImgToDb();
    } else {
        return 0;
    }
}
#endif


/**
 * Cancel the running process.  Will not be handled immediately. 
 */
void
 TskAutoDbJava::stopAddImage()
{
    if (tsk_verbose)
        tsk_fprintf(stderr, "TskAutoDbJava::stopAddImage: Stop request received\n");
    
    m_stopped = true;
    setStopProcessing();
    // flag is checked every time processFile() is called
}

/**
 * Set the current image's timezone
 */
void
TskAutoDbJava::setTz(string tzone)
{
    m_curImgTZone = tzone;
}

/**
 * Set the object ID for the data source
 */
void 
TskAutoDbJava::setDatasourceObjId(int64_t img_id)
{
    m_curImgId = img_id;
}

TSK_RETVAL_ENUM
TskAutoDbJava::processFile(TSK_FS_FILE * fs_file, const char *path)
{
    // Check if the process has been canceled
     if (m_stopped) {
        if (tsk_verbose)
            tsk_fprintf(stderr, "TskAutoDbJava::processFile: Stop request detected\n");
        return TSK_STOP;
    }

    /* Update the current directory, which can be used to show
     * progress.  If we get a directory, then use its name.  We
     * do this so that when we are searching for orphan files, then
     * we at least show $OrphanFiles as status.  The secondary check
     * is to grab the parent folder from files once we return back 
     * into a folder when we are doing our depth-first recursion. */
    if (isDir(fs_file)) {
        m_curDirAddr = fs_file->name->meta_addr;
        tsk_take_lock(&m_curDirPathLock);
        m_curDirPath = string(path) + fs_file->name->name;
        tsk_release_lock(&m_curDirPathLock);
    }
    else if (m_curDirAddr != fs_file->name->par_addr) {
        m_curDirAddr = fs_file->name->par_addr;
        tsk_take_lock(&m_curDirPathLock);
        m_curDirPath = path;
        tsk_release_lock(&m_curDirPathLock);
    }

    /* process the attributes.  The case of having 0 attributes can occur
     * with virtual / sparse files and HFS directories.  
     * At some point, this can probably be cleaned
     * up if TSK is more consistent about if there should always be an 
     * attribute or not.  Sometimes, none of the attributes are added
     * because of their type and we always want to add a reference to 
     * every file. */
    TSK_RETVAL_ENUM retval = TSK_OK;
    m_attributeAdded = false;
    if (tsk_fs_file_attr_getsize(fs_file) > 0) {
        retval = processAttributes(fs_file, path);
    }

    // insert a general row if we didn't add a specific attribute one
    if ((retval == TSK_OK) && (m_attributeAdded == false)) {
        retval = insertFileData(fs_file, NULL, path);
    }
    
    // reset the file id
    m_curFileId = 0;

    if (retval == TSK_STOP)
        return TSK_STOP;
    else 
        return TSK_OK;
}


// we return only OK or STOP -- errors are registered only and OK is returned. 
TSK_RETVAL_ENUM
TskAutoDbJava::processAttribute(TSK_FS_FILE * fs_file,
    const TSK_FS_ATTR * fs_attr, const char *path)
{
    // add the file metadata for the default attribute type
    if (isDefaultType(fs_file, fs_attr)) {

        if (insertFileData(fs_attr->fs_file, fs_attr, path) == TSK_ERR) {
            registerError();
            return TSK_OK;
        }
        else {
            m_attributeAdded = true;
        }
    }

    return TSK_OK;
}


/**
* Callback invoked per every unallocated block in the filesystem
* Creates file ranges and file entries 
* A single file entry per consecutive range of blocks
* @param a_block block being walked
* @param a_ptr a pointer to an UNALLOC_BLOCK_WLK_TRACK struct
* @returns TSK_WALK_CONT if continue, otherwise TSK_WALK_STOP if stop processing requested
*/
TSK_WALK_RET_ENUM TskAutoDbJava::fsWalkUnallocBlocksCb(const TSK_FS_BLOCK *a_block, void *a_ptr) {
    UNALLOC_BLOCK_WLK_TRACK * unallocBlockWlkTrack = (UNALLOC_BLOCK_WLK_TRACK *) a_ptr;

    if (unallocBlockWlkTrack->tskAutoDbJava.m_stopAllProcessing)
        return TSK_WALK_STOP;

    // initialize if this is the first block
    if (unallocBlockWlkTrack->isStart) {
        unallocBlockWlkTrack->isStart = false;
        unallocBlockWlkTrack->curRangeStart = a_block->addr;
        unallocBlockWlkTrack->prevBlock = a_block->addr;
        unallocBlockWlkTrack->size = unallocBlockWlkTrack->fsInfo.block_size;
        unallocBlockWlkTrack->nextSequenceNo = 0;
        return TSK_WALK_CONT;
    }

    // We want to keep consecutive blocks in the same run, so simply update prevBlock and the size
    // if this one is consecutive with the last call. But, if we have hit the max chunk
    // size, then break up this set of consecutive blocks.
    if ((a_block->addr == unallocBlockWlkTrack->prevBlock + 1) && ((unallocBlockWlkTrack->maxChunkSize <= 0) ||
            (unallocBlockWlkTrack->size < unallocBlockWlkTrack->maxChunkSize))) {
        unallocBlockWlkTrack->prevBlock = a_block->addr;
		unallocBlockWlkTrack->size += unallocBlockWlkTrack->fsInfo.block_size;
        return TSK_WALK_CONT;
    }

    // this block is not contiguous with the previous one or we've hit the maximum size; create and add a range object
    const uint64_t rangeStartOffset = unallocBlockWlkTrack->curRangeStart * unallocBlockWlkTrack->fsInfo.block_size 
        + unallocBlockWlkTrack->fsInfo.offset;
    const uint64_t rangeSizeBytes = (1 + unallocBlockWlkTrack->prevBlock - unallocBlockWlkTrack->curRangeStart) 
        * unallocBlockWlkTrack->fsInfo.block_size;
    unallocBlockWlkTrack->ranges.push_back(TSK_DB_FILE_LAYOUT_RANGE(rangeStartOffset, rangeSizeBytes, unallocBlockWlkTrack->nextSequenceNo++));

    // Return (instead of adding this run) if we are going to:
    // a) Make one big file with all unallocated space (minChunkSize == 0)
    // or
    // b) Only make an unallocated file once we have at least chunkSize bytes
    // of data in our current run (minChunkSize > 0)
    // In either case, reset the range pointers and add this block to the size
    if ((unallocBlockWlkTrack->minChunkSize == 0) ||
        ((unallocBlockWlkTrack->minChunkSize > 0) &&
        (unallocBlockWlkTrack->size < unallocBlockWlkTrack->minChunkSize))) {

        unallocBlockWlkTrack->size += unallocBlockWlkTrack->fsInfo.block_size;
        unallocBlockWlkTrack->curRangeStart = a_block->addr;
        unallocBlockWlkTrack->prevBlock = a_block->addr;
        return TSK_WALK_CONT;
    }
    
    // at this point we are either chunking and have reached the chunk limit
    // or we're not chunking. Either way we now add what we've got to the DB
    int64_t fileObjId = 0;
    TskAutoDbJava & tskAutoDbJava = unallocBlockWlkTrack->tskAutoDbJava;
    if (tskAutoDbJava.addUnallocBlockFile(tskAutoDbJava.m_curUnallocDirId,
        unallocBlockWlkTrack->fsObjId, unallocBlockWlkTrack->size, unallocBlockWlkTrack->ranges, fileObjId, tskAutoDbJava.m_curImgId) == TSK_ERR) {
            // @@@ Handle error -> Don't have access to registerError() though...
    }

    // reset
    unallocBlockWlkTrack->curRangeStart = a_block->addr;
    unallocBlockWlkTrack->prevBlock = a_block->addr;
    unallocBlockWlkTrack->size = unallocBlockWlkTrack->fsInfo.block_size; // The current block is part of the new range
    unallocBlockWlkTrack->ranges.clear();
    unallocBlockWlkTrack->nextSequenceNo = 0;

    //we don't know what the last unalloc block is in advance
    //and will handle the last range in addFsInfoUnalloc()
    
    return TSK_WALK_CONT;
}


/**
* Add unallocated space for the given file system to the database.
* Create files for consecutive unalloc block ranges.
* @param dbFsInfo fs to process
* @returns TSK_OK on success, TSK_ERR on error
*/
TSK_RETVAL_ENUM TskAutoDbJava::addFsInfoUnalloc(const TSK_DB_FS_INFO & dbFsInfo) {

    // Unalloc space is handled separately for APFS
    if (dbFsInfo.fType == TSK_FS_TYPE_APFS) {
        return TSK_OK;
    }

    //open the fs we have from database
    TSK_FS_INFO * fsInfo = tsk_fs_open_img(m_img_info, dbFsInfo.imgOffset, dbFsInfo.fType);
    if (fsInfo == NULL) {
        tsk_error_set_errstr2("TskAutoDbJava::addFsInfoUnalloc: error opening fs at offset %" PRIdOFF, dbFsInfo.imgOffset);
        registerError();
        return TSK_ERR;
    }

    //create a "fake" dir to hold the unalloc files for the fs
    if (addUnallocFsBlockFilesParent(dbFsInfo.objId, m_curUnallocDirId, m_curImgId) == TSK_ERR) {
        tsk_error_set_errstr2("addFsInfoUnalloc: error creating dir for unallocated space");
        registerError();
        return TSK_ERR;
    }

    //walk unalloc blocks on the fs and process them
    //initialize the unalloc block walk tracking 
    UNALLOC_BLOCK_WLK_TRACK unallocBlockWlkTrack(*this, *fsInfo, dbFsInfo.objId, m_minChunkSize, m_maxChunkSize);
    uint8_t block_walk_ret = tsk_fs_block_walk(fsInfo, fsInfo->first_block, fsInfo->last_block, (TSK_FS_BLOCK_WALK_FLAG_ENUM)(TSK_FS_BLOCK_WALK_FLAG_UNALLOC | TSK_FS_BLOCK_WALK_FLAG_AONLY), 
        fsWalkUnallocBlocksCb, &unallocBlockWlkTrack);

    if (block_walk_ret == 1) {
        stringstream errss;
        tsk_fs_close(fsInfo);
        errss << "TskAutoDbJava::addFsInfoUnalloc: error walking fs unalloc blocks, fs id: ";
        errss << unallocBlockWlkTrack.fsObjId;
        tsk_error_set_errstr2("%s", errss.str().c_str());
        registerError();
        return TSK_ERR;
    }

    if(m_stopAllProcessing) {
        tsk_fs_close(fsInfo);
        return TSK_OK;
    }

    // handle creation of the last range
    // make range inclusive from curBlockStart to prevBlock
    const uint64_t byteStart = unallocBlockWlkTrack.curRangeStart * fsInfo->block_size + fsInfo->offset;
    const uint64_t byteLen = (1 + unallocBlockWlkTrack.prevBlock - unallocBlockWlkTrack.curRangeStart) * fsInfo->block_size;
    unallocBlockWlkTrack.ranges.push_back(TSK_DB_FILE_LAYOUT_RANGE(byteStart, byteLen, unallocBlockWlkTrack.nextSequenceNo++));
    int64_t fileObjId = 0;

    if (addUnallocBlockFile(m_curUnallocDirId, dbFsInfo.objId, unallocBlockWlkTrack.size, unallocBlockWlkTrack.ranges, fileObjId, m_curImgId) == TSK_ERR) {
        registerError();
        tsk_fs_close(fsInfo);
        return TSK_ERR;
    }
    
    //cleanup 
    tsk_fs_close(fsInfo);

    return TSK_OK; 
}

/**
* Process all unallocated space for this disk image and create "virtual" files with layouts
* @returns TSK_OK on success, TSK_ERR on error
*/
TSK_RETVAL_ENUM TskAutoDbJava::addUnallocSpaceToDb() {
    if (m_stopAllProcessing) {
        return TSK_OK;
    }

    size_t numVsP = 0;
    size_t numFs = 0;
    size_t numPool = 0;

    TSK_RETVAL_ENUM retFsSpace = addUnallocFsSpaceToDb(numFs);
    TSK_RETVAL_ENUM retVsSpace = addUnallocVsSpaceToDb(numVsP);
    TSK_RETVAL_ENUM retPoolSpace = addUnallocatedPoolBlocksToDb(numPool);

    //handle case when no fs and no vs partitions and no pools
    TSK_RETVAL_ENUM retImgFile = TSK_OK;
    if (numVsP == 0 && numFs == 0 && numPool == 0) {
        retImgFile = addUnallocImageSpaceToDb();
    }
    
    
    if (retFsSpace == TSK_ERR || retVsSpace == TSK_ERR || retPoolSpace == TSK_ERR || retImgFile == TSK_ERR)
        return TSK_ERR;
    else
        return TSK_OK;
}


/**
* Process each file system in the database and add its unallocated sectors to virtual files. 
* @param numFs (out) number of filesystems found
* @returns TSK_OK on success, TSK_ERR on error (if some or all fs could not be processed)
*/
TSK_RETVAL_ENUM TskAutoDbJava::addUnallocFsSpaceToDb(size_t & numFs) {

    if(m_stopAllProcessing) {
        return TSK_OK;
    }

    numFs = m_savedFsInfo.size();
    TSK_RETVAL_ENUM allFsProcessRet = TSK_OK;
    for (vector<TSK_DB_FS_INFO>::iterator it = m_savedFsInfo.begin(); it!= m_savedFsInfo.end(); ++it) {
        if (m_stopAllProcessing) {
            break;
        }
        if (addFsInfoUnalloc(*it) == TSK_ERR)
            allFsProcessRet = TSK_ERR;
    }

    //TODO set parent_path for newly created virt dir/file hierarchy for consistency

    return allFsProcessRet;
}

/**
* Process each volume in the database and add its unallocated sectors to virtual files. 
* @param numVsP (out) number of vs partitions found
* @returns TSK_OK on success, TSK_ERR on error
*/
TSK_RETVAL_ENUM TskAutoDbJava::addUnallocVsSpaceToDb(size_t & numVsP) {

    numVsP = m_savedVsPartInfo.size();

    //get fs infos to see if this vspart has fs
    for (vector<TSK_DB_VS_PART_INFO>::const_iterator it = m_savedVsPartInfo.begin();
            it != m_savedVsPartInfo.end(); ++it) {
        if (m_stopAllProcessing) {
            break;
        }
        const TSK_DB_VS_PART_INFO &vsPart = *it;

        //interested in unalloc, meta, or alloc and no fs
        if ( (vsPart.flags & (TSK_VS_PART_FLAG_UNALLOC | TSK_VS_PART_FLAG_META)) == 0 ) {
            //check if vspart has no fs
            bool hasFs = false;
            for (vector<TSK_DB_FS_INFO>::const_iterator itFs = m_savedFsInfo.begin();
               itFs != m_savedFsInfo.end(); ++itFs) {
               const TSK_DB_FS_INFO & fsInfo = *itFs;

               TSK_DB_OBJECT* fsObjInfo = NULL;
               if (getObjectInfo(fsInfo.objId, &fsObjInfo) == TSK_ERR ) {
                   stringstream errss;
                   errss << "addUnallocVsSpaceToDb: error getting object info for fs from db, objId: " << fsInfo.objId;
                   tsk_error_set_errstr2("%s", errss.str().c_str());
                   registerError();
                   return TSK_ERR;
               }

               if (fsObjInfo->parObjId == vsPart.objId) {
                   hasFs = true;
                   break;
               }
            }
        
            if (hasFs == true) {
                //skip processing this vspart
                continue;
            }

            // Check if the volume contains a pool
            bool hasPool = false;
            for (std::map<int64_t, int64_t>::iterator iter = m_poolOffsetToParentId.begin(); iter != m_poolOffsetToParentId.end(); ++iter) {
                if (iter->second == vsPart.objId) {
                    hasPool = true;
                }
            }
            if (hasPool) {
                // Skip processing this vspart
                continue;
            }

        }

        // Get sector size and image offset from parent vs info
        // Get parent id of this vs part
        TSK_DB_OBJECT* vsPartObj = NULL;     
        if (getObjectInfo(vsPart.objId, &vsPartObj) == TSK_ERR) {
            stringstream errss;
            errss << "addUnallocVsSpaceToDb: error getting object info for vs part from db, objId: " << vsPart.objId;
            tsk_error_set_errstr2("%s", errss.str().c_str());
            registerError();
            return TSK_ERR;
        }
        if (vsPartObj == NULL) {
            return TSK_ERR;
        }

        TSK_DB_VS_INFO* vsInfo = NULL;
        for (vector<TSK_DB_VS_INFO>::iterator itVs = m_savedVsInfo.begin();
                itVs != m_savedVsInfo.end(); ++itVs) {
            TSK_DB_VS_INFO* temp_vs_info = &(*itVs);
            if (temp_vs_info->objId == vsPartObj->parObjId) {
                vsInfo = temp_vs_info;
            }
        }

        if (vsInfo == NULL ) {
            stringstream errss;
            errss << "addUnallocVsSpaceToDb: error getting volume system info from db, objId: " << vsPartObj->parObjId;
            tsk_error_set_errstr2("%s", errss.str().c_str());
            registerError();
            return TSK_ERR;
        }

        // Create an unalloc file with unalloc part, with vs part as parent
        vector<TSK_DB_FILE_LAYOUT_RANGE> ranges;
        const uint64_t byteStart = vsInfo->offset + vsInfo->block_size * vsPart.start;
        const uint64_t byteLen = vsInfo->block_size * vsPart.len; 
        TSK_DB_FILE_LAYOUT_RANGE tempRange(byteStart, byteLen, 0);
        ranges.push_back(tempRange);
        int64_t fileObjId = 0;
        if (addUnallocBlockFile(vsPart.objId, 0, tempRange.byteLen, ranges, fileObjId, m_curImgId) == TSK_ERR) {
            registerError();
            return TSK_ERR;
        }
    }

    return TSK_OK;
}


/**
* Adds unalloc space for the image if there is no volumes and no file systems.
*
* @returns TSK_OK on success, TSK_ERR on error
*/
TSK_RETVAL_ENUM TskAutoDbJava::addUnallocImageSpaceToDb() {

    const TSK_OFF_T imgSize = getImageSize();
    if (imgSize == -1) {
        tsk_error_set_errstr("addUnallocImageSpaceToDb: error getting current image size, can't create unalloc block file for the image.");
        registerError();
        return TSK_ERR;
    }
    else {
        TSK_DB_FILE_LAYOUT_RANGE tempRange(0, imgSize, 0);
        //add unalloc block file for the entire image
        vector<TSK_DB_FILE_LAYOUT_RANGE> ranges;
        ranges.push_back(tempRange);
        int64_t fileObjId = 0;
        if (TSK_ERR == addUnallocBlockFile(m_curImgId, 0, imgSize, ranges, fileObjId, m_curImgId)) {
            return TSK_ERR;
        }
    }
    return TSK_OK;
}

/**
* Returns the directory currently being analyzed by processFile().
* Safe to use from another thread than processFile().
*
* @returns curDirPath string representing currently analyzed directory
*/
const std::string TskAutoDbJava::getCurDir() {
    string curDirPath;
    tsk_take_lock(&m_curDirPathLock);
    curDirPath = m_curDirPath;
    tsk_release_lock(&m_curDirPathLock);
    return curDirPath;
}
