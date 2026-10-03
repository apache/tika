/*
 ** dataModel_SleuthkitJNI
 ** The Sleuth Kit 
 **
 ** Brian Carrier [carrier <at> sleuthkit [dot] org]
 ** Copyright (c) 2010-2018 Brian Carrier.  All Rights reserved
 **
 ** This software is distributed under the Common Public License 1.0
 **
 */
#include "tsk/tsk_tools_i.h"
#include "tsk/auto/tsk_case_db.h"
#include "tsk/hashdb/tsk_hash_info.h"
#include "tsk/auto/tsk_is_image_supported.h"
#include "tsk/img/img_writer.h"
#include "tsk/img/raw.h"
#include "auto_db_java.h"
#if HAVE_LIBEWF
#include "tsk/img/ewf.h"
#include "tsk/img/tsk_img_i.h"
#endif
#include "jni.h"
#include "dataModel_SleuthkitJNI.h"
#include <locale.h>
#include <time.h>
#include <vector>
#include <map>
#include <string>
#include <algorithm>
#include <sstream>

using std::string;
using std::vector;
using std::map;
using std::stringstream;

static std::vector<TSK_HDB_INFO *> hashDbs;

/*
* JNI file handle structure encapsulates both
* TSK_FS_FILE file handle and TSK_FS_ATTR attribute
* to support multiple attributes for the same file.
* TSK_FS_FILE still needs be maintained for opening and closing.
*/
typedef struct {
    uint32_t tag; 
    TSK_FS_FILE *fs_file; 
    TSK_FS_ATTR *fs_attr; 
} TSK_JNI_FILEHANDLE;
#define TSK_JNI_FILEHANDLE_TAG 0x10101214

//stack-allocated buffer size for read method
#define FIXED_BUF_SIZE (16 * 1024)

/**
* Sets flag to throw an TskCoreException back up to the Java code with a specific message.
* Note: exception is thrown to Java code after the native function returns
* not when setThrowTskCoreError() is invoked - this must be addressed in the code following the exception 
* @param the java environment to send the exception to
* @param msg message string
 */
static void
setThrowTskCoreError(JNIEnv * env, const char *msg)
{
    jclass exception;
    exception = env->FindClass("org/sleuthkit/datamodel/TskCoreException");
    env->ThrowNew(exception, msg);
}

/**
* Sets flag to throw an TskCoreException back up to the Java code with the currently set error message.
* Note: exception is thrown to Java code after the native function returns
* not when setThrowTskCoreError() is invoked - this must be addressed in the code following the exception 
* @param the java environment to send the exception to
*/
static void
setThrowTskCoreError(JNIEnv * env)
{
    const char *msg = tsk_error_get();
    setThrowTskCoreError(env, msg);
}

/**
* Sets flag to throw an TskDataException back up to the Java code with a specific message.
* Note: exception is thrown to Java code after the native function returns
* not when setThrowTskDataError() is invoked - this must be addressed in the code following the exception 
* @param the java environment to send the exception to
* @param msg message string
 */
static void
setThrowTskDataError(JNIEnv * env, const char *msg)
{
    jclass exception;
    exception = env->FindClass("org/sleuthkit/datamodel/TskDataException");
    env->ThrowNew(exception, msg);
}

#if 0
/**
* Sets flag to throw an TskDataException back up to the Java code with the currently set error message.
* Note: exception is thrown to Java code after the native function returns
* not when setThrowTskDataError() is invoked - this must be addressed in the code following the exception 
* @param the java environment to send the exception to
*/
static void
setThrowTskDataError(JNIEnv * env)
{
    const char *msg = tsk_error_get();
    setThrowTskDataError(env, msg);
}
#endif


/***** Methods to cast from jlong to data type and check tags
 They all throw an exception if the incorrect type is passed in. *****/
static TSK_IMG_INFO *
castImgInfo(JNIEnv * env, jlong ptr)
{
    TSK_IMG_INFO *lcl = (TSK_IMG_INFO *) ptr;
    if (!lcl || lcl->tag != TSK_IMG_INFO_TAG) {
        setThrowTskCoreError(env, "Invalid IMG_INFO object");
        return 0;
    }
    return lcl;
}


static TSK_VS_INFO *
castVsInfo(JNIEnv * env, jlong ptr)
{
    TSK_VS_INFO *lcl = (TSK_VS_INFO *) ptr;
    if (!lcl || lcl->tag != TSK_VS_INFO_TAG) {
        setThrowTskCoreError(env, "Invalid VS_INFO object");
        return 0;
    }
    // verify that image handle is still open
    if (!castImgInfo(env, (jlong) lcl->img_info)) {
        return 0;
    }
    return lcl;
}

static TSK_VS_PART_INFO *
castVsPartInfo(JNIEnv * env, jlong ptr)
{
    TSK_VS_PART_INFO *lcl = (TSK_VS_PART_INFO *) ptr;
    if (!lcl || lcl->tag != TSK_VS_PART_INFO_TAG) {
        setThrowTskCoreError(env, "Invalid VS_PART_INFO object");
        return 0;
    }
    // verify that all handles are still open
    if (!castVsInfo(env, (jlong) lcl->vs)) {
        return 0;
    }
    return lcl;
}

static TSK_POOL_INFO *
castPoolInfo(JNIEnv * env, jlong ptr)
{
    TSK_POOL_INFO *lcl = (TSK_POOL_INFO *)ptr;
    if (!lcl || lcl->tag != TSK_POOL_INFO_TAG) {
        setThrowTskCoreError(env, "Invalid TSK_POOL_INFO object");
        return 0;
    }
    return lcl;
}

static TSK_FS_INFO *
castFsInfo(JNIEnv * env, jlong ptr)
{
    TSK_FS_INFO *lcl = (TSK_FS_INFO *) ptr;
    if (!lcl || lcl->tag != TSK_FS_INFO_TAG) {
        setThrowTskCoreError(env, "Invalid FS_INFO object");
        return 0;
    }
    // verify that image handle is still open
    if (!castImgInfo(env, (jlong) lcl->img_info)) {
        return 0;
    }
    return lcl;
}


static TSK_FS_FILE *
castFsFile(JNIEnv * env, jlong ptr)
{
	TSK_FS_FILE *lcl = (TSK_FS_FILE *)ptr;
	if (lcl == NULL || lcl->tag != TSK_FS_FILE_TAG) {
		setThrowTskCoreError(env, "Invalid FS_FILE object");
		return 0;
	}
	// verify that file system handle is still open
	if (!castFsInfo(env, (jlong)lcl->fs_info)) {
		return 0;
	}
	return lcl;
}

static TSK_JNI_FILEHANDLE *
castJniFileHandle(JNIEnv * env, jlong ptr)
{
    TSK_JNI_FILEHANDLE *lcl = (TSK_JNI_FILEHANDLE *) ptr;
    if (!lcl || lcl->tag != TSK_JNI_FILEHANDLE_TAG) {
        setThrowTskCoreError(env, "Invalid TSK_JNI_FILEHANDLE object");
        return 0;
    }
    // verify that all handles are still open
    if (!castFsFile(env, (jlong) lcl->fs_file)) {
        return 0;
    }
    return lcl;
}

static TskCaseDb * 
castCaseDb(JNIEnv * env, jlong ptr)
{
    TskCaseDb *lcl = ((TskCaseDb *) ptr);
    if (lcl == NULL || lcl->m_tag != TSK_CASE_DB_TAG) {
        setThrowTskCoreError(env,
            "Invalid TskCaseDb object");
        return 0;
    }

    return lcl;
}

/**
 * Convert a jstring (UTF-8) to a TCHAR to pass into TSK methods.
 * @param buffer Buffer to store resulting string into
 * @param size Length of buffer
 * @param strJ string to convert
 * @returns 1 on error 
 */
static int
toTCHAR(JNIEnv * env, TSK_TCHAR * buffer, size_t size, jstring strJ)
{
    jboolean isCopy;
    char *str8 = (char *) env->GetStringUTFChars(strJ, &isCopy);

#ifdef TSK_WIN32
    // Windows TCHAR is UTF16 in Windows, so convert
    UTF16 *utf16 = (UTF16 *) buffer;
    UTF8 *utf8 = (UTF8 *) str8;;
    TSKConversionResult retval;
    size_t lengthOfUtf8 = strlen(str8);

    retval =
        tsk_UTF8toUTF16((const UTF8 **) &utf8, &utf8[lengthOfUtf8],
        &utf16, &utf16[size], TSKlenientConversion);
    if (retval != TSKconversionOK) {
        tsk_error_set_errno(TSK_ERR_IMG_CONVERT);
        tsk_error_set_errstr
            ("toTCHAR: Error converting UTF8 %s to UTF16, error %d",
            utf8, retval);
        env->ReleaseStringUTFChars(strJ, str8);
        return 1;
    }

    // "utf16" now points to last char. Need to NULL terminate the string.
    *utf16 = '\0';

#else
    // nothing to convert.  Keep it as UTF8
    strncpy((char *)&buffer[0], str8, size);
#endif

    env->ReleaseStringUTFChars(strJ, str8);
    return 0;
}

/**
 * Opens an existing hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param pathJ The path to the hash database.
 * @return A handle for the hash database.
 */
JNIEXPORT jint JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbOpenNat(JNIEnv * env,
    jclass obj, jstring pathJ) 
{
    TSK_TCHAR pathT[1024];
    toTCHAR(env, pathT, 1024, pathJ);
    TSK_HDB_INFO *db = tsk_hdb_open(pathT, TSK_HDB_OPEN_NONE);
    if (!db)
    {
        setThrowTskCoreError(env, tsk_error_get_errstr());
        return -1;
    }
    
    // The index of the pointer in the vector is used as a handle for the
    // database.
    hashDbs.push_back(db);
    return (jint)hashDbs.size();
}

/**
 * Creates a new hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param pathJ The path to the hash database.
 * @return A handle for the hash database.
 */
JNIEXPORT jint JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbNewNat(JNIEnv * env,
    jclass obj, jstring pathJ)
{
    TSK_TCHAR pathT[1024];
    toTCHAR(env, pathT, 1024, pathJ);
    if (tsk_hdb_create(pathT)) {
        setThrowTskCoreError(env, tsk_error_get_errstr());
        return -1;
    }

    TSK_HDB_INFO *db = tsk_hdb_open(pathT, TSK_HDB_OPEN_NONE);
    if (!db) {
        setThrowTskCoreError(env, tsk_error_get_errstr());
        return -1;
    }

    // The index of the pointer in the vector is used as a handle for the
    // database.
    hashDbs.push_back(db);    
    return (jint)hashDbs.size();
}

/**
 * Begins a hash database transaction.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return 1 on error and 0 on success.
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbBeginTransactionNat(
    JNIEnv *env, jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle - 1);
    if (!db) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    return tsk_hdb_begin_transaction(db);
}

/**
 * Commits a hash database transaction.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return 1 on error and 0 on success.
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbCommitTransactionNat(
    JNIEnv *env, jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle - 1);
    if (!db) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    return tsk_hdb_commit_transaction(db);
}

/**
 * Rolls back a hash database transaction.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return 1 on error and 0 on success.
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbRollbackTransactionNat(
    JNIEnv *env, jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (!db) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    return tsk_hdb_rollback_transaction(db);
}

/**
 * Adds data to a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param filenameJ Name of the file that was hashed (can be null).
 * @param hashMd5J MD5 hash of file contents (can be null).
 * @param hashSha1J SHA-1 hash of file contents (can be null).
 * @param hashSha256J Text of SHA256 hash (can be null).
 * @param dbHandle A handle for the hash database.
 * @return 1 on error and 0 on success.
 */
JNIEXPORT jint JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbAddEntryNat(JNIEnv * env,
    jclass obj, jstring filenameJ, jstring hashMd5J, jstring hashSha1J, jstring hashSha256J,
    jstring commentJ, jint dbHandle)
{
    if((size_t) dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    TSK_HDB_INFO * db = hashDbs.at(dbHandle-1);
    if(!db) {
        setThrowTskCoreError(env, "Invalid database handle");
        return 1;
    }

    if(!db->accepts_updates()) {
        setThrowTskCoreError(env, "Database does not accept updates");
        return 1;
    }

    jboolean isCopy;
    const char * name = filenameJ ? (const char *) env->GetStringUTFChars(filenameJ, &isCopy) : NULL;
    const char * md5 = hashMd5J ? (const char *) env->GetStringUTFChars(hashMd5J, &isCopy) : NULL;
    const char * sha1 = hashSha1J ? (const char *) env->GetStringUTFChars(hashSha1J, &isCopy) : NULL;
    const char * sha256 = hashSha256J ? (const char *) env->GetStringUTFChars(hashSha256J, &isCopy) : NULL;
    const char * comment = commentJ ? (const char *) env->GetStringUTFChars(commentJ, &isCopy) : NULL;
   
    if (tsk_hdb_add_entry(db, name, md5, sha1, sha256, comment)) {
        setThrowTskCoreError(env, tsk_error_get_errstr());
    }

    if (filenameJ) {
        env->ReleaseStringUTFChars(filenameJ, (const char *) name);
    }

    if (hashMd5J) { 
        env->ReleaseStringUTFChars(hashMd5J, (const char *) md5);
    }

    if (hashSha1J) {
        env->ReleaseStringUTFChars(hashSha1J, (const char *) sha1);
    }

    if (hashSha256J) {
        env->ReleaseStringUTFChars(hashSha256J, (const char *) sha256);
    }

    if (commentJ) {
        env->ReleaseStringUTFChars(commentJ, (const char *) comment);
    }

    return 0;
}

/**
 * Queries whether or not a hash database accepts updates.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return True if hash database can be updated.
 */
JNIEXPORT jboolean JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbIsUpdateableNat(JNIEnv * env,
    jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    return (jboolean)(tsk_hdb_accepts_updates(db) == static_cast<uint8_t>(1));
}

/**
 * Queries whether or not a hash database can be re-indexed. Only text-format
 * databases with external indexes can be re-indexed.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return True if hash database can be indexed.
 */
JNIEXPORT jboolean JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbIsReindexableNat(JNIEnv * env,
    jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    return (jboolean)((tsk_hdb_uses_external_indexes(db) == 1) && 
                      (tsk_hdb_is_idx_only(db) == 0));
}
 
/**
 * Gets the path of a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return Path to the hash database or "None" if no path is available.
 */
JNIEXPORT jstring JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbPathNat(JNIEnv * env,
    jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    jstring jPath = NULL;
    const TSK_TCHAR *dbPath = tsk_hdb_get_db_path(db);
    if (NULL != dbPath) {
        const size_t pathLength = TSTRLEN(dbPath);
        char *cPath = (char*)tsk_malloc((pathLength + 1) * sizeof(char));
        snprintf(cPath, pathLength + 1, "%" PRIttocTSK, dbPath);
        jPath = env->NewStringUTF(cPath);
        free(cPath);
    }
    else {
        jPath = env->NewStringUTF("None");
    }
    return jPath;
}

/*
 * Gets the path of the external MD5 hash index for a text-format database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return Path to the requested index or "None" if no path is available.
 */
JNIEXPORT jstring JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbIndexPathNat(JNIEnv * env,
    jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    // Currently only supporting md5 indexes through Java binding.
    jstring jPath = NULL;
    const TSK_TCHAR *indexPath = tsk_hdb_get_idx_path(db, TSK_HDB_HTYPE_MD5_ID);
    if (NULL != indexPath) {
        const size_t pathLength = TSTRLEN(indexPath);
        char *cPath = (char*)tsk_malloc((pathLength + 1) * sizeof(char));
        snprintf(cPath, pathLength + 1, "%" PRIttocTSK, indexPath);
        jPath = env->NewStringUTF(cPath);
        free(cPath);
    }
    else {
        jPath = env->NewStringUTF("None");
    }
    return jPath;
}

/**
 * Queries whether the hash database is actually an external index for a
 * text-format database that is being used for simple yes/no look ups in
 * place of the roginal hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return True if the hash database is an external index serving as a 
 * database.
 */
JNIEXPORT jboolean JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbIsIdxOnlyNat(JNIEnv * env,
    jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    return (jboolean)(tsk_hdb_is_idx_only(db) == static_cast<uint8_t>(1));
}

/**
 * Gets the display name of a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return The display name.
 */
JNIEXPORT jstring JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbGetDisplayName
  (JNIEnv * env, jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    jstring j_name = NULL;
    const char *db_name = tsk_hdb_get_display_name(db);
    if (NULL != db_name) {
        j_name = env->NewStringUTF(db_name);
    }
    return j_name;
}

/**
 * Closes all open hash databases.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 */
JNIEXPORT void JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbCloseAll(JNIEnv * env,
    jclass obj) 
{
    for (std::vector<TSK_HDB_INFO *>::iterator it = hashDbs.begin(); it != hashDbs.end(); ++it) {
        if (NULL != *it) {
            tsk_hdb_close(*it);
        }
    }

    hashDbs.clear();
}

/**
 * Closes a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 */
JNIEXPORT void JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbClose(JNIEnv * env,
    jclass obj, jint dbHandle) 
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return;
    }

    tsk_hdb_close(db);

    // Do NOT erase the element because that would shift the indices,
    // messing up the existing handles.
    hashDbs.at(dbHandle-1) = NULL;
}

/**
 * Looks up a hash in a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return True if the hash is found in the hash database, false otherwise.
 */
JNIEXPORT jboolean JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbLookup
(JNIEnv * env, jclass obj, jstring hash, jint dbHandle) 
{
    if ((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    jboolean isCopy;
    const char *cHashStr = (const char *) env->GetStringUTFChars(hash, &isCopy);
    jboolean file_known = false;
    int8_t retval = tsk_hdb_lookup_str(db, cHashStr, TSK_HDB_FLAG_QUICK, NULL, NULL);
    if (retval == -1) {
        setThrowTskCoreError(env, tsk_error_get_errstr());
    } 
    else if (retval) {
        file_known = true;
    }

    env->ReleaseStringUTFChars(hash, (const char *) cHashStr);

    return file_known;
}

/**
 * Looks up a hash in a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return A HashInfo object if the hash is found, NULL otherwise.
 */
JNIEXPORT jobject JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbLookupVerbose
(JNIEnv * env, jclass obj, jstring hash, jint dbHandle) {
    if ((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return NULL;
    }
    
    jboolean isCopy;
    const char *inputHash = (const char *) env->GetStringUTFChars(hash, &isCopy);
    TskHashInfo result; 
    int8_t returnCode = tsk_hdb_lookup_verbose_str(db, inputHash, (void*)&result);
    env->ReleaseStringUTFChars(hash, (const char *) inputHash);
    
    if (returnCode == -1) {
        setThrowTskCoreError(env, tsk_error_get_errstr());
        return NULL;
    }
    else if (returnCode == 0) {
        return NULL;
    }

    // Convert the hashes from the hash database so they can be written into
    // the Java version of a HashInfo object.
    const char *md5 = result.hashMd5.c_str();
    jstring md5j = env->NewStringUTF(md5);
            
    const char *sha1 = result.hashSha1.c_str();
    jstring sha1j = env->NewStringUTF(sha1);
            
    const char *sha256 = result.hashSha2_256.c_str();
    jstring sha256j = env->NewStringUTF(sha256);

    // Create and return a Java HashInfo object.
    jclass clazz;
    clazz = env->FindClass("org/sleuthkit/datamodel/HashHitInfo");
    jmethodID ctor = env->GetMethodID(clazz, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    jmethodID addName = env->GetMethodID(clazz, "addName", "(Ljava/lang/String;)V");
    jmethodID addComment = env->GetMethodID(clazz, "addComment", "(Ljava/lang/String;)V");
    jobject hashInfo = env->NewObject(clazz, ctor, md5j, sha1j, sha256j);
    for (std::vector<std::string>::iterator it = result.fileNames.begin(); it != result.fileNames.end(); ++it) {
        jstring namej = env->NewStringUTF((*it).c_str());
        env->CallVoidMethod(hashInfo, addName, namej);
    }
    for (std::vector<std::string>::iterator it = result.comments.begin(); it != result.comments.end(); ++it) {
        jstring commentj = env->NewStringUTF((*it).c_str());
        env->CallVoidMethod(hashInfo, addComment, commentj);
    }
    return hashInfo;
}

/*
 * Initialize a process for adding an image to a case database.
 *
 * @param env Pointer to java environment.
 * @param obj Pointer the Java class object.
 * @param timeZone The time zone for the image.
 * @param addUnallocSpace Pass true to create virtual files for unallocated space. Ignored if addFileSystems is false.
 * @param skipFatFsOrphans Pass true to skip processing of orphan files for FAT file systems. Ignored if addFileSystems is false.
 *
 * @return A pointer to the process (TskAutoDbJava object) or NULL on error.
 */
JNIEXPORT jlong JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_initAddImgNat(JNIEnv * env,
    jclass obj, jobject callbackObj, jstring timeZone, jboolean addUnallocSpace, jboolean skipFatFsOrphans) {
    return Java_org_sleuthkit_datamodel_SleuthkitJNI_initializeAddImgNat(env, obj, callbackObj, timeZone, true, addUnallocSpace, skipFatFsOrphans);
}

/*
 * Initialize a process for adding an image to a case database.
 *
 * @param env Pointer to java environment.
 * @param obj Pointer the Java class object.
 * @param timeZone The time zone for the image.
 * @param addFileSystems Pass true to attempt to add file systems within the image to the case database.
 * @param addUnallocSpace Pass true to create virtual files for unallocated space. Ignored if addFileSystems is false.
 * @param skipFatFsOrphans Pass true to skip processing of orphan files for FAT file systems. Ignored if addFileSystems is false.
 *
 * @return A pointer to the process (TskAutoDbJava object) or NULL on error.
 */
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_initializeAddImgNat(JNIEnv * env, jclass obj,
    jobject callbackObj, jstring timeZone, jboolean addFileSystems, jboolean addUnallocSpace, jboolean skipFatFsOrphans) {
    jboolean isCopy;

    if (env->GetStringUTFLength(timeZone) > 0) {
        const char *tzstr = env->GetStringUTFChars(timeZone, &isCopy);

        if (strlen(tzstr) > 64) {
            env->ReleaseStringUTFChars(timeZone, tzstr);
            stringstream ss;
            ss << "Timezone is too long";
            setThrowTskCoreError(env, ss.str().c_str());
            return 0;
        }

        char envstr[70];
        snprintf(envstr, 70, "TZ=%s", tzstr);
        env->ReleaseStringUTFChars(timeZone, tzstr);

        if (0 != putenv(envstr)) {
            stringstream ss;
            ss << "Error setting timezone environment, using: ";
            ss << envstr;
            setThrowTskCoreError(env, ss.str().c_str());
            return 0;
        }

        /* we should be checking this somehow */
        TZSET();
    }

    TskAutoDbJava *tskAutoJava = new TskAutoDbJava();
    if (tskAutoJava == NULL) {
        setThrowTskCoreError(env, "Error creating TskAutoDbJava");
        return 0;
    }

    // set the options flags
    tskAutoJava->setAddFileSystems(addFileSystems?true:false);
    if (addFileSystems) {
        if (addUnallocSpace) {
            // Minimum size of unalloc files: 500 MB, maximum size: 1 GB
            tskAutoJava->setAddUnallocSpace((int64_t)500 * 1024 * 1024, (int64_t)1024 * 1024 * 1024);
        }
        else {
            tskAutoJava->setAddUnallocSpace(false);
        }
        tskAutoJava->setNoFatFsOrphans(skipFatFsOrphans?true:false);
    } else {
        tskAutoJava->setAddUnallocSpace(false);
        tskAutoJava->setNoFatFsOrphans(true);
    }

    // Set up the callbacks
    if (TSK_ERR == tskAutoJava->initializeJni(env, callbackObj)) {
        setThrowTskCoreError(env, "Error initializing JNI callbacks");
        return 0;
    }

    return (jlong)tskAutoJava;
}

/*
 * Add an image to a database using a pre-created process, which can be cancelled.
 * MUST call commitAddImg or revertAddImg afterwards once runAddImg returns.  If there is an 
 * error, you do not need to call revert or commit and the 'process' handle will be deleted.
 *
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param process the add-image process created by initAddImgNat
 * @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID)
 * @param paths array of strings from java, the paths to the image parts
 * @param numImgs number of image parts
 * @param timeZone the timezone the image is from
 */
JNIEXPORT void JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_runOpenAndAddImgNat(JNIEnv * env,
    jclass obj, jlong process, jstring deviceId, jobjectArray paths, jint numImgs, jstring timeZone) {

    TskAutoDbJava *tskAuto = ((TskAutoDbJava *) process);
    if (!tskAuto || tskAuto->m_tag != TSK_AUTO_TAG) {
        setThrowTskCoreError(env, 
            "runOpenAndAddImgNat: Invalid TskAutoDbJava object passed in");
        return;
    }

    jboolean isCopy;
    const char *device_id = NULL;
    if (NULL != deviceId) {    
        device_id = (const char *) env->GetStringUTFChars(deviceId, &isCopy);
        if (NULL == device_id) {
            setThrowTskCoreError(env, "runOpenAndAddImgNat: Can't convert data source id string");
            return;
        }
    }

    // Get pointers to each of the image file names.
    char **imagepaths8 = (char **) tsk_malloc(numImgs * sizeof(char *));
    if (imagepaths8 == NULL) {
        setThrowTskCoreError(env);
        return;
    }
    for (int i = 0; i < numImgs; i++) {
        jstring jsPath = (jstring) env->GetObjectArrayElement(paths,
                i);
        imagepaths8[i] =
            (char *) env->
            GetStringUTFChars(jsPath, &isCopy);
        if (imagepaths8[i] == NULL) {
            setThrowTskCoreError(env,
                "runOpenAndAddImgNat: Can't convert path strings.");
            // @@@ should cleanup here paths that have been converted in imagepaths8[i]
            return;
        }
    }

    // Set the time zone.
    if (env->GetStringLength(timeZone) > 0) {
        const char *time_zone = env->GetStringUTFChars(timeZone, &isCopy);
        tskAuto->setTz(string(time_zone));
        env->ReleaseStringUTFChars(timeZone, time_zone);
    }

    // Add the data source.
    uint8_t ret = 0;
    if ( (ret = tskAuto->startAddImage((int) numImgs, imagepaths8,
        TSK_IMG_TYPE_DETECT, 0, device_id)) != 0) {
        stringstream msgss;
        msgss << "Errors occurred while ingesting image " << std::endl;
        vector<TskAuto::error_record> errors = tskAuto->getErrorList();
        for (size_t i = 0; i < errors.size(); i++) {
            msgss << (i+1) << ". ";
            msgss << (TskAuto::errorRecordToString(errors[i]));
            msgss << " " << std::endl;
        }

        if (ret == 1) {
            // Fatal error
            setThrowTskCoreError(env, msgss.str().c_str());
        }
        else if (ret == 2) {
            // Non-fatal error
            setThrowTskDataError(env, msgss.str().c_str());
        }
    }

    // @@@ SHOULD WE CLOSE HERE before we commit / revert etc.
    //close image first before freeing the image paths
    tskAuto->closeImage();

    // Cleanup
    for (int i = 0; i < numImgs; i++) {
        jstring jsPath = (jstring)
            env->GetObjectArrayElement(paths, i);
        env->
            ReleaseStringUTFChars(jsPath, imagepaths8[i]);
        env->DeleteLocalRef(jsPath);
    }
    free(imagepaths8);
    env->ReleaseStringUTFChars(deviceId, (const char *) device_id);

    // // Must call finishAddImgNat to free the TskAutoDb
}

/*
* Add an image to a database using a pre-created process, which can be cancelled.
* MUST call commitAddImg or revertAddImg afterwards once runAddImg returns.  If there is an
* error, you do not need to call revert or commit and the 'process' handle will be deleted.
*
* @param env pointer to java environment this was called from
* @param obj the java object this was called from
* @param process the add-image process created by initAddImgNat
* @param deviceId An ASCII-printable identifier for the device associated with the data source that is intended to be unique across multiple cases (e.g., a UUID)
* @param a_img_info image info object
* @param img_id The object ID of the image in the database
* @param timeZone the timezone the image is from
*/
JNIEXPORT void JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_runAddImgNat(JNIEnv * env,
    jclass obj, jlong process, jstring deviceId, jlong a_img_info, jlong img_id, jstring timeZone, jstring imageWriterPathJ) {
    
    TskAutoDbJava *tskAuto = ((TskAutoDbJava *)process);
    if (!tskAuto || tskAuto->m_tag != TSK_AUTO_TAG) {
        setThrowTskCoreError(env,
            "runAddImgNat: Invalid TskAutoDbJava object passed in");
        return;
    }

    jboolean isCopy;
    const char *device_id = NULL;
    if (NULL != deviceId) {
        device_id = (const char *)env->GetStringUTFChars(deviceId, &isCopy);
        if (NULL == device_id) {
            setThrowTskCoreError(env, "runAddImgNat: Can't convert data source id string");
            return;
        }
    }

    // Set the data source object ID
    tskAuto->setDatasourceObjId(img_id);

    // Set the time zone.
    if (env->GetStringLength(timeZone) > 0) {
        const char *time_zone = env->GetStringUTFChars(timeZone, &isCopy);
        tskAuto->setTz(string(time_zone));
        env->ReleaseStringUTFChars(timeZone, time_zone);
    }

    // Set up the TSK_IMG_INFO object
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);

    // Set up image writer, if the output path is present
    if (env->GetStringLength(imageWriterPathJ) > 0) {
        const char *imageWriterPath = env->GetStringUTFChars(imageWriterPathJ, &isCopy);
        if (TSK_OK != tskAuto->enableImageWriter(imageWriterPath)) {
            env->ReleaseStringUTFChars(imageWriterPathJ, imageWriterPath);
            setThrowTskCoreError(env,
                "runAddImgNat: error enabling image writer.");
            return;
        }
        env->ReleaseStringUTFChars(imageWriterPathJ, imageWriterPath);
    }
    else {
        tskAuto->disableImageWriter();
    }

    // Add the data source.
    uint8_t ret = 0;
    if ((ret = tskAuto->startAddImage(img_info, device_id)) != 0) {
        stringstream msgss;
        msgss << "Errors occurred while ingesting image " << std::endl;
        vector<TskAuto::error_record> errors = tskAuto->getErrorList();
        for (size_t i = 0; i < errors.size(); i++) {
            msgss << (i + 1) << ". ";
            msgss << (TskAuto::errorRecordToString(errors[i]));
            msgss << " " << std::endl;
        }

        if (ret == 1) {
            // Fatal error
            setThrowTskCoreError(env, msgss.str().c_str());
        }
        else if (ret == 2) {
            // Non-fatal error
            setThrowTskDataError(env, msgss.str().c_str());
        }
    }

    // @@@ SHOULD WE CLOSE HERE before we commit / revert etc.
    //close image first before freeing the image paths
    tskAuto->closeImage();

    // Cleanup
    env->ReleaseStringUTFChars(deviceId, (const char *)device_id);

    // Must call finishAddImgNat to free the TskAutoDb
}


/*
 * Cancel the given add-image process.
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param process the add-image process created by initAddImgNat
 */
JNIEXPORT void JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_stopAddImgNat(JNIEnv * env,
    jclass obj, jlong process) {
    TskAutoDbJava *tskAuto = ((TskAutoDbJava *) process);
    if (!tskAuto || tskAuto->m_tag != TSK_AUTO_TAG) {
        setThrowTskCoreError(env,
            "stopAddImgNat: Invalid TskAutoDbJava object passed in");
        return;
    }
    tskAuto->stopAddImage();
}


/*
* Completes the given add-image process. Deletes the 'process' handle and
* returns the ID of the added image.
* @param env pointer to java environment this was called from
* @param obj the java object this was called from
* @param process the add-image process created by initAddImgNat
*/
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_finishAddImgNat(JNIEnv * env,
    jclass obj, jlong process) {
    TskAutoDbJava *tskAuto = ((TskAutoDbJava *)process);
    if (!tskAuto || tskAuto->m_tag != TSK_AUTO_TAG) {
        setThrowTskCoreError(env,
            "commitAddImgNat: Invalid TskAutoDb object passed in");
        return -1;
    }
    int64_t imgId = tskAuto->getImageID();
    tskAuto->close();
    delete tskAuto;
    tskAuto = 0;
    if (imgId == -1) {
        setThrowTskCoreError(env);
        return -1;
    }
    return imgId;
}


/*
 * Open an image pointer for the given image.
 * @return the created TSK_IMG_INFO pointer
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param paths the paths to the image parts
 * @param num_imgs number of image parts
 * @param sector_size the sector size (use '0' for autodetect)
 */
JNIEXPORT jlong JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_openImgNat(JNIEnv * env,
    jclass obj, jobjectArray paths, jint num_imgs, jint sector_size) {
    TSK_IMG_INFO *img_info;
    jboolean isCopy;

    // get pointers to each of the file names
    char **imagepaths8 = (char **)tsk_malloc(num_imgs * sizeof(char *));
    if (imagepaths8 == NULL) {
        setThrowTskCoreError(env);
        return 0;
    }
    for (int i = 0; i < num_imgs; i++) {
        imagepaths8[i] =
            (char *)env->
            GetStringUTFChars((jstring)env->GetObjectArrayElement(paths,
                i), &isCopy);
        // @@@ Error check
    }

    // open the image
    img_info =
        tsk_img_open_utf8((int)num_imgs, imagepaths8, TSK_IMG_TYPE_DETECT,
            sector_size);
    if (img_info == NULL) {
        setThrowTskCoreError(env, tsk_error_get());
    }

    // cleanup
    for (int i = 0; i < num_imgs; i++) {
        env->
            ReleaseStringUTFChars((jstring)
                env->GetObjectArrayElement(paths, i), imagepaths8[i]);
    }
    free(imagepaths8);

    return (jlong) img_info;
}

/*
 * Get the full list of paths associated with an image.
 */
JNIEXPORT jobjectArray JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getPathsForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }

    char **img_ptrs;
#ifdef TSK_WIN32
    // convert image paths to UTF-8
    img_ptrs = (char **)tsk_malloc(img_info->num_img * sizeof(char *));
    if (img_ptrs == NULL) {
        return (jobjectArray)env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    for (int i = 0; i < img_info->num_img; i++) {
        char * img2 = (char*)tsk_malloc(1024 * sizeof(char));
        UTF8 *ptr8;
        UTF16 *ptr16;

        ptr8 = (UTF8 *)img2;
        ptr16 = (UTF16 *)img_info->images[i];

        uint8_t retval =
            tsk_UTF16toUTF8_lclorder((const UTF16 **)&ptr16, (UTF16 *)
                & ptr16[TSTRLEN(img_info->images[i]) + 1], &ptr8,
                (UTF8 *)((uintptr_t)ptr8 + 1024), TSKlenientConversion);
        if (retval != TSKconversionOK) {
            tsk_error_reset();
            tsk_error_set_errno(TSK_ERR_AUTO_UNICODE);
            tsk_error_set_errstr("Error converting image to UTF-8\n");
            return (jobjectArray)env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
        }
        img_ptrs[i] = img2;
    }
#else 
    img_ptrs = img_info->images;
#endif

    jobjectArray path_list = (jobjectArray)env->NewObjectArray(img_info->num_img, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    for (int i = 0; i < img_info->num_img; i++) {
        env->SetObjectArrayElement(path_list, i, env->NewStringUTF(img_ptrs[i]));
    }

    return path_list;
}


/*
 * Get the size of an image.
 */
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getSizeForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }

    return img_info->size;
}


/*
 * Get the type of an image.
 */
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getTypeForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }

    return img_info->itype;
}


/*
* Get the computed sector size of an image.
*/
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getSectorSizeForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }

    return img_info->sector_size;
}

/*
* Get the md5 hash of an image.
*/
JNIEXPORT jstring JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getMD5HashForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }
    // env->NewStringUTF(img_ptrs[i])
#if HAVE_LIBEWF 
    if (img_info->itype == TSK_IMG_TYPE_EWF_EWF) {
        IMG_EWF_INFO *ewf_info = (IMG_EWF_INFO *)img_info;
        if (ewf_info->md5hash_isset) {
            return env->NewStringUTF(ewf_info->md5hash);
        }
    }
#endif
    return env->NewStringUTF("");
}

/*
* Get the sha1 hash of an image.
*/
JNIEXPORT jstring JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getSha1HashForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }
    // env->NewStringUTF(img_ptrs[i])
#if HAVE_LIBEWF 
    if (img_info->itype == TSK_IMG_TYPE_EWF_EWF) {
        IMG_EWF_INFO *ewf_info = (IMG_EWF_INFO *)img_info;
        if (ewf_info->sha1hash_isset) {
            return env->NewStringUTF(ewf_info->sha1hash);
        }
    }
#endif
    return env->NewStringUTF("");
}

/*
* Get the collection details of an image.
*/
JNIEXPORT jstring JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getCollectionDetailsForImageNat(JNIEnv * env,
    jclass obj, jlong a_img_info) {

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }
    // env->NewStringUTF(img_ptrs[i])
#if HAVE_LIBEWF 
    if (img_info->itype == TSK_IMG_TYPE_EWF_EWF) {
        IMG_EWF_INFO *ewf_info = (IMG_EWF_INFO *)img_info;
        ewf_get_details(ewf_info);
    }
#endif
    return env->NewStringUTF("");
}

/*
 * Open the volume system at the given offset
 * @return the created TSK_VS_INFO pointer
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_img_info the pointer to the parent img object
 * @param vsOffset the offset of the volume system in bytes
 */
JNIEXPORT jlong JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_openVsNat
    (JNIEnv * env, jclass obj, jlong a_img_info, jlong vsOffset) {
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }
    TSK_VS_INFO *vs_info;

    vs_info = tsk_vs_open(img_info, vsOffset, TSK_VS_TYPE_DETECT);
    if (vs_info == NULL) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jlong) vs_info;
}


/*
 * Open volume with the given id from the given volume system
 * @return the created TSK_VS_PART_INFO pointer
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_vs_info the pointer to the parent vs object
 * @param vol_id the id of the volume to get
 */
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_openVolNat(JNIEnv * env,
    jclass obj, jlong a_vs_info, jlong vol_id)
{
    TSK_VS_INFO *vs_info = castVsInfo(env, a_vs_info);
    if (vs_info == 0) {
        //exception already set
        return 0;
    }
    const TSK_VS_PART_INFO *vol_part_info;

    vol_part_info = tsk_vs_part_get(vs_info, (TSK_PNUM_T) vol_id);
    if (vol_part_info == NULL) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jlong) vol_part_info;
}

/*
* Open pool with the given offset
* @return the created TSK_POOL_INFO pointer
* @param env pointer to java environment this was called from
* @param obj the java object this was called from
* @param a_img_info the pointer to the parent img object
* @param offset the offset in bytes to the pool
*/
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_openPoolNat(JNIEnv * env,
    jclass obj, jlong a_img_info, jlong offset)
{
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }

    const TSK_POOL_INFO *pool = tsk_pool_open_img_sing(img_info, offset, TSK_POOL_TYPE_DETECT);
    if (pool == NULL) {
        tsk_error_print(stderr);
        if (tsk_error_get_errno() == TSK_ERR_POOL_UNSUPTYPE)
            tsk_pool_type_print(stderr);
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jlong) pool;
}

/*
* Create new image info to use with a specific pool volume
* @return the created TSK_IMG_INFO pointer
* @param env pointer to java environment this was called from
* @param obj the java object this was called from
* @param a_pool_info the pointer to the pool object
* @param pool_block the block number of the pool volume
*/
JNIEXPORT jlong JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_getImgInfoForPoolNat
(JNIEnv * env, jclass obj, jlong a_pool_info, jlong pool_block) {

    TSK_POOL_INFO *pool_info = castPoolInfo(env, a_pool_info);
    if (pool_info == 0) {
        //exception already set
        return 0;
    }
    TSK_IMG_INFO *img_info = pool_info->get_img_info(pool_info, pool_block);

    return (jlong)img_info;
}

/*
 * Open file system with the given offset
 * @return the created TSK_FS_INFO pointer
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_img_info the pointer to the parent img object
 * @param fs_offset the offset in bytes to the file system 
 */
JNIEXPORT jlong JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_openFsNat
    (JNIEnv * env, jclass obj, jlong a_img_info, jlong fs_offset) {
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return 0;
    }
    TSK_FS_INFO *fs_info;
    fs_info =
        tsk_fs_open_img(img_info, (TSK_OFF_T) fs_offset,
        TSK_FS_TYPE_DETECT);
    if (fs_info == NULL) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jlong) fs_info;
}


/*
 * Open the file with the given id in the given file system
 * @return the created TSK_JNI_FILEHANDLE pointer, set throw exception on error
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_fs_info the pointer to the parent file system object
 * @param file_id id of the file to open
 * @param attr_type type of the file attribute to open
 * @param attr_id id of the file attribute to open
 */
JNIEXPORT jlong JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_openFileNat(JNIEnv * env,
    jclass obj, jlong a_fs_info, jlong file_id, jint attr_type, jint attr_id)
{
    TSK_FS_INFO *fs_info = castFsInfo(env, a_fs_info);
    if (fs_info == 0) {
        //exception already set
        return 0;
    }

    
    TSK_FS_FILE *file_info;
    //open file
    file_info = tsk_fs_file_open_meta(fs_info, NULL, (TSK_INUM_T) file_id);
    if (file_info == NULL) {
        setThrowTskCoreError(env, tsk_error_get());
        return 0;
    }

    //open attribute
    const TSK_FS_ATTR * tsk_fs_attr = 
        tsk_fs_file_attr_get_type(file_info, (TSK_FS_ATTR_TYPE_ENUM)attr_type, (uint16_t)attr_id, 1);
    if (tsk_fs_attr == NULL) {
        tsk_fs_file_close(file_info);
        setThrowTskCoreError(env, tsk_error_get());
        return 0;
    }

    //allocate file handle structure to encapsulate file and attribute
    TSK_JNI_FILEHANDLE * fileHandle = 
        (TSK_JNI_FILEHANDLE *) tsk_malloc(sizeof(TSK_JNI_FILEHANDLE));
    if (fileHandle == NULL) {
        tsk_fs_file_close(file_info);
        setThrowTskCoreError(env, "Could not allocate memory for TSK_JNI_FILEHANDLE");
        return 0;
    }

    fileHandle->tag = TSK_JNI_FILEHANDLE_TAG;
    fileHandle->fs_file = file_info;
    fileHandle->fs_attr = const_cast<TSK_FS_ATTR*>(tsk_fs_attr);

    return (jlong)fileHandle;
}


/** move a local buffer into a new Java array.
 * @param env JNI env
 * @param buf Buffer to copy from
 * @param len Length of bytes in buf
 * @returns Pointer to newly created java byte array or NULL if there is an error
 */
#if 0
static jbyteArray
copyBufToByteArray(JNIEnv * env, const char *buf, ssize_t len)
{
    jbyteArray return_array = env->NewByteArray(len);
    if (return_array == NULL) {
        setThrowTskCoreError(env, "NewByteArray returned error while getting an array to copy buffer into.");
        return 0;
    }
    env->SetByteArrayRegion(return_array, 0, len, (jbyte*)buf);

    return return_array;
}
#endif

/** move a local buffer into an existing Java array.
 * @param env JNI env
 * @param jbuf Buffer to copy to
 * @param buf Buffer to copy from
 * @param len Length of bytes in buf
 * @returns number of bytes copied or -1 on error
 */
inline static ssize_t
copyBufToByteArray(JNIEnv * env, jbyteArray jbuf, const char *buf, ssize_t len)
{
    env->SetByteArrayRegion(jbuf, 0, (jsize)len, (jbyte*)buf);
    return len;
}

/*
 * Read bytes from the given image
 * @return number of bytes read from the image, -1 on error
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_img_info the pointer to the image object
 * @param offset the offset in bytes to start at
 * @param len number of bytes to read
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_readImgNat(JNIEnv * env,
    jclass obj, jlong a_img_info, jbyteArray jbuf, jlong offset, jlong len)
{
    //use fixed size stack-allocated buffer if possible
    char fixed_buf [FIXED_BUF_SIZE];

    char * buf = fixed_buf;
    bool dynBuf = false;
    if (len > FIXED_BUF_SIZE) {
        dynBuf = true;
        buf = (char *) tsk_malloc((size_t) len);
        if (buf == NULL) {
            setThrowTskCoreError(env);
            return -1;
        }
    }

    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        if (dynBuf) {
            free(buf);
        }
        //exception already set
        return -1;
    }

    ssize_t bytesread =
        tsk_img_read(img_info, (TSK_OFF_T) offset, buf, (size_t) len);
    if (bytesread == -1) {
        if (dynBuf) {
            free(buf);
        }
        setThrowTskCoreError(env, tsk_error_get());
        return -1;
    }

    // package it up for return
    // adjust number bytes to copy
    ssize_t copybytes = bytesread;
    jsize jbuflen = env->GetArrayLength(jbuf);
    if (jbuflen < copybytes)
        copybytes = jbuflen;

    ssize_t copiedbytes = copyBufToByteArray(env, jbuf, buf, copybytes);
    if (dynBuf) {
        free(buf);
    }
    if (copiedbytes == -1) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jint)copiedbytes;
}

/*
* Read bytes from the given pool
* @return number of bytes read from the pool, -1 on error
* @param env pointer to java environment this was called from
* @param obj the java object this was called from
* @param a_pool_info the pointer to the pool object
* @param jbuf the buffer to write to
* @param offset the offset in bytes to start at
* @param len number of bytes to read
*/
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_readPoolNat(JNIEnv * env,
    jclass obj, jlong a_pool_info, jbyteArray jbuf, jlong offset, jlong len)
{
    //use fixed size stack-allocated buffer if possible
    char fixed_buf[FIXED_BUF_SIZE];

    char * buf = fixed_buf;
    bool dynBuf = false;
    if (len > FIXED_BUF_SIZE) {
        dynBuf = true;
        buf = (char *)tsk_malloc((size_t)len);
        if (buf == NULL) {
            setThrowTskCoreError(env);
            return -1;
        }
    }

    TSK_POOL_INFO *pool_info = castPoolInfo(env, a_pool_info);
    if (pool_info == 0) {
        //exception already set
        if (dynBuf) {
            free(buf);
        }
        return -1;
    }

    ssize_t bytesread = tsk_pool_read(pool_info, (TSK_DADDR_T)offset, buf,
        (size_t)len);
    if (bytesread == -1) {
        setThrowTskCoreError(env, tsk_error_get());
        if (dynBuf) {
            free(buf);
        }
        return -1;
    }

    // package it up for return
    // adjust number bytes to copy
    ssize_t copybytes = bytesread;
    jsize jbuflen = env->GetArrayLength(jbuf);
    if (jbuflen < copybytes)
        copybytes = jbuflen;

    ssize_t copiedbytes = copyBufToByteArray(env, jbuf, buf, copybytes);
    if (dynBuf) {
        free(buf);
    }
    if (copiedbytes == -1) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jint)copiedbytes;
}


/*
 * Read bytes from the given volume system
 * @return number of bytes read from the volume system, -1 on error
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_vs_info the pointer to the volume system object
 * @param offset the offset in bytes to start at
 * @param len number of bytes to read
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_readVsNat(JNIEnv * env,
    jclass obj, jlong a_vs_info, jbyteArray jbuf, jlong offset, jlong len)
{
    //use fixed size stack-allocated buffer if possible
    char fixed_buf [FIXED_BUF_SIZE];

    char * buf = fixed_buf;
    bool dynBuf = false;
    if (len > FIXED_BUF_SIZE) {
        dynBuf = true;
        buf = (char *) tsk_malloc((size_t) len);
        if (buf == NULL) {
            setThrowTskCoreError(env);
            return -1;
        }
    }

    TSK_VS_INFO *vs_info = castVsInfo(env, a_vs_info);
    if (vs_info == 0) {
        //exception already set
        if (dynBuf) {
            free(buf);
        }
        return -1;
    }

    ssize_t bytesread = tsk_vs_read_block(vs_info, (TSK_DADDR_T) offset, buf,
        (size_t) len);
    if (bytesread == -1) {
        setThrowTskCoreError(env, tsk_error_get());
        if (dynBuf) {
            free(buf);
        }
        return -1;
    }

    // package it up for return
    // adjust number bytes to copy
    ssize_t copybytes = bytesread;
    jsize jbuflen = env->GetArrayLength(jbuf);
    if (jbuflen < copybytes)
        copybytes = jbuflen;

    ssize_t copiedbytes = copyBufToByteArray(env, jbuf, buf, copybytes);
    if (dynBuf) {
        free(buf);
    }
    if (copiedbytes == -1) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jint)copiedbytes;
}


/*
 * Read bytes from the given volume
 * @return number of bytes read from the volume or -1 on error
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_vol_info the pointer to the volume object
 * @param offset the offset in bytes to start at
 * @param len number of bytes to read
 */

JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_readVolNat(JNIEnv * env,
    jclass obj, jlong a_vol_info, jbyteArray jbuf, jlong offset, jlong len)
{
    //use fixed size stack-allocated buffer if possible
    char fixed_buf [FIXED_BUF_SIZE];

    char * buf = fixed_buf;
    bool dynBuf = false;
    if (len > FIXED_BUF_SIZE) {
        dynBuf = true;
        buf = (char *) tsk_malloc((size_t) len);
        if (buf == NULL) {
            setThrowTskCoreError(env);
            return -1;
        }
    }

    TSK_VS_PART_INFO *vol_part_info = castVsPartInfo(env, a_vol_info);
    if (vol_part_info == 0) {
        if (dynBuf) {
            free(buf);
        }
        //exception already set
        return -1;
    }
    ssize_t bytesread =
        tsk_vs_part_read(vol_part_info, (TSK_OFF_T) offset, buf,
        (size_t) len);
    if (bytesread == -1) {
        setThrowTskCoreError(env, tsk_error_get());
        if (dynBuf) {
            free(buf);
        }
        return -1;
    }

    // package it up for return
    // adjust number bytes to copy
    ssize_t copybytes = bytesread;
    jsize jbuflen = env->GetArrayLength(jbuf);
    if (jbuflen < copybytes)
        copybytes = jbuflen;

    ssize_t copiedbytes = copyBufToByteArray(env, jbuf, buf, copybytes);
    if (dynBuf) {
        free(buf);
    }
    if (copiedbytes == -1) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jint)copiedbytes;
}


/*
 * Read bytes from the given file system
 * @return number of bytes read from the file system, -1 on error
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_fs_info the pointer to the file system object
 * @param offset the offset in bytes to start at
 * @param len number of bytes to read
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_readFsNat(JNIEnv * env,
    jclass obj, jlong a_fs_info, jbyteArray jbuf, jlong offset, jlong len)
{
    //use fixed size stack-allocated buffer if possible
    char fixed_buf [FIXED_BUF_SIZE];

    char * buf = fixed_buf;
    bool dynBuf = false;
    if (len > FIXED_BUF_SIZE) {
        dynBuf = true;
        buf = (char *) tsk_malloc((size_t) len);
        if (buf == NULL) {
            setThrowTskCoreError(env);
            return -1;
        }
    }

    TSK_FS_INFO *fs_info = castFsInfo(env, a_fs_info);
    if (fs_info == 0) {
        if (dynBuf) {
            free(buf);
        }
        //exception already set
        return -1;
    }

    ssize_t bytesread =
        tsk_fs_read(fs_info, (TSK_OFF_T) offset, buf, (size_t) len);
    if (bytesread == -1) {
        if (dynBuf) {
            free(buf);
        }
        setThrowTskCoreError(env, tsk_error_get());
        return -1;
    }

    // package it up for return
    // adjust number bytes to copy
    ssize_t copybytes = bytesread;
    jsize jbuflen = env->GetArrayLength(jbuf);
    if (jbuflen < copybytes)
        copybytes = jbuflen;

    ssize_t copiedbytes = copyBufToByteArray(env, jbuf, buf, copybytes);
    if (dynBuf) {
        free(buf);
    }
    if (copiedbytes == -1) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jint)copiedbytes;
}


/**
 * Flag used by readFileNat to specify if the offset is relative to the start of the file
 * or the start of the slack space 
 */
typedef enum {
    TSK_FS_FILE_READ_OFFSET_TYPE_START_OF_FILE = 0x00,
    TSK_FS_FILE_READ_OFFSET_TYPE_START_OF_SLACK = 0x01,
} TSK_FS_FILE_READ_OFFSET_TYPE_ENUM;

/*
 * Read bytes from the given file
 * @return number of bytes read, or -1 on error
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_file_handle the pointer to the TSK_JNI_FILEHANDLE object
 * @param jbuf jvm allocated buffer to read to
 * @param offset the offset in bytes to start at
 * @param len number of bytes to read
 */
JNIEXPORT jint JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_readFileNat(JNIEnv * env,
    jclass obj, jlong a_file_handle, jbyteArray jbuf, jlong offset, jint offset_type, jlong len)
{
    //use fixed size stack-allocated buffer if possible
    char fixed_buf [FIXED_BUF_SIZE];

    char * buf = fixed_buf;
    bool dynBuf = false;
    if (len > FIXED_BUF_SIZE) {
        dynBuf = true;
        buf = (char *) tsk_malloc((size_t) len);
        if (buf == NULL) {
            setThrowTskCoreError(env);
            return -1;
        }
    }

    const TSK_JNI_FILEHANDLE *file_handle = castJniFileHandle(env, a_file_handle);
    if (file_handle == 0) {
        if (dynBuf) {
            free(buf);
        }
        //exception already set
        return -1;
    }

    TSK_FS_ATTR * tsk_fs_attr = file_handle->fs_attr;

    TSK_FS_FILE_READ_FLAG_ENUM readFlag = TSK_FS_FILE_READ_FLAG_NONE;
    TSK_OFF_T readOffset = (TSK_OFF_T) offset;
    if(offset_type == TSK_FS_FILE_READ_OFFSET_TYPE_START_OF_SLACK){
        readFlag = TSK_FS_FILE_READ_FLAG_SLACK;
        readOffset += tsk_fs_attr->nrd.initsize;
    }

    //read attribute
    ssize_t bytesread = tsk_fs_attr_read(tsk_fs_attr,  readOffset, buf, (size_t) len,
        readFlag);
    if (bytesread == -1) {
        if (dynBuf) {
            free(buf);
        }
        setThrowTskCoreError(env, tsk_error_get());
        return -1;
    }

    // package it up for return
    // adjust number bytes to copy
    ssize_t copybytes = bytesread;
    jsize jbuflen = env->GetArrayLength(jbuf);
    if (jbuflen < copybytes)
        copybytes = jbuflen;

    ssize_t copiedbytes = copyBufToByteArray(env, jbuf, buf, copybytes);
    if (dynBuf) {
        free(buf);
    }
    if (copiedbytes == -1) {
        setThrowTskCoreError(env, tsk_error_get());
    }
    return (jint)copiedbytes;
}


/**
 * Runs istat on a given file and saves the output to a temp file.
 *
 * @returns -1 on error (and throws exception)
 */
JNIEXPORT jint JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_saveFileMetaDataTextNat
  (JNIEnv *env, jclass obj, jlong a_file_handle, jstring a_tmp_path)
{
    const TSK_JNI_FILEHANDLE *file_handle = castJniFileHandle(env, a_file_handle);
    if (file_handle == 0) {
        //exception already set
        return -1;
    }
    
    // check the pointers
    if (file_handle->fs_file == NULL || file_handle->fs_file->fs_info == NULL || file_handle->fs_file->meta == NULL) {
        setThrowTskCoreError(env, "NULL pointers for istat file.");
        return -1;
    }
    TSK_FS_INFO *fs_info = file_handle->fs_file->fs_info;   

    // open a file to write the details to
    jboolean isCopy;
    char *str8 = (char *) env->GetStringUTFChars(a_tmp_path, &isCopy);
    FILE *hFile = fopen(str8, "w");
    if (hFile == NULL) {
        env->ReleaseStringUTFChars(a_tmp_path, str8);
        setThrowTskCoreError(env, "Couldn't open istat temp file for writing.");
        return -1;
    }
    env->ReleaseStringUTFChars(a_tmp_path, str8);
    
    if (fs_info->istat(fs_info, TSK_FS_ISTAT_RUNLIST, hFile, file_handle->fs_file->meta->addr, 0, 0) != 0) {
        fclose(hFile);
        setThrowTskCoreError(env);
        return -1;
    }

    fclose(hFile);
    return 0;
}

/*
 * Close the given image
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_img_info the pointer to the image object
 */
JNIEXPORT void JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_closeImgNat(JNIEnv * env,
    jclass obj, jlong a_img_info)
{
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    if (img_info == 0) {
        //exception already set
        return;
    }
    tsk_img_close(img_info);
}

/*
 * Close the given volume system
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_vs_info the pointer to the volume system object
 */
JNIEXPORT void JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_closeVsNat
    (JNIEnv * env, jclass obj, jlong a_vs_info) {
    TSK_VS_INFO *vs_info = castVsInfo(env, a_vs_info);
    if (vs_info == 0) {
        //exception already set
        return;
    }
    tsk_vs_close(vs_info);
}

/*
 * Close the given file system
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_fs_info the pointer to the file system object
 */
JNIEXPORT void JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_closeFsNat
    (JNIEnv * env, jclass obj, jlong a_fs_info) {
    TSK_FS_INFO *fs_info = castFsInfo(env, a_fs_info);
    if (fs_info == 0) {
        //exception already set
        return;
    }
    tsk_fs_close(fs_info);
}

/*
* Close the given pool
* @param env pointer to java environment this was called from
* @param obj the java object this was called from
* @param a_pool_info the pointer to the pool object
*/
JNIEXPORT void JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_closePoolNat
(JNIEnv * env, jclass obj, jlong a_pool_info) {
    TSK_POOL_INFO *pool_info = castPoolInfo(env, a_pool_info);
    if (pool_info == 0) {
        //exception already set
        return;
    }
    tsk_pool_close(pool_info);
}

/*
 * Close the given file
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_file_info the pointer to the file object
 */
JNIEXPORT void JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_closeFileNat(JNIEnv * env,
    jclass obj, jlong a_file_info)
{
    TSK_JNI_FILEHANDLE *file_handle = castJniFileHandle(env, a_file_info);
    if (file_handle == 0) {
        //exception already set
        return;
    }
    
    TSK_FS_FILE * file_info = file_handle->fs_file;
    tsk_fs_file_close(file_info); //also closes the attribute

    file_handle->fs_file = NULL;
    file_handle->fs_attr = NULL;
    file_handle->tag = 0;
    free (file_handle);
}

/*
 * Get the current Sleuthkit version number
 * @return the version string
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 */
JNIEXPORT jstring JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_getVersionNat(JNIEnv * env,
    jclass obj)
{
    const char *cversion = tsk_version_get_str();
    jstring jversion = (*env).NewStringUTF(cversion);
    return jversion;
}

/*
 * Get the current directory being analyzed during AddImage
 * @return the path of the current directory
 *
 */
JNIEXPORT jstring JNICALL
    Java_org_sleuthkit_datamodel_SleuthkitJNI_getCurDirNat
    (JNIEnv * env,jclass obj, jlong dbHandle)
{
    TskAutoDbJava *tskAuto = ((TskAutoDbJava *) dbHandle);
    const std::string curDir = tskAuto->getCurDir();
    jstring jdir = (*env).NewStringUTF(curDir.c_str());
    return jdir;
}

/*
 * Enable verbose logging and redirect stderr to the given log file.
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param logPath The log file to append to.
 */
JNIEXPORT void JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_startVerboseLoggingNat
    (JNIEnv * env, jclass obj, jstring logPath)
{
    jboolean isCopy;
    char *str8 = (char *) env->GetStringUTFChars(logPath, &isCopy);
    if (freopen(str8, "a", stderr) == NULL) {
        env->ReleaseStringUTFChars(logPath, str8);
        setThrowTskCoreError(env, "Couldn't open verbose log file for appending.");
        return;
    }
    env->ReleaseStringUTFChars(logPath, str8);
    tsk_verbose++;
}

/*
 * Creates an MD5 index for a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 */
JNIEXPORT void JNICALL
Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbCreateIndexNat (JNIEnv * env,
    jclass obj, jint dbHandle)
{
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return;
    }

    TSK_TCHAR idx_type[1024];
    if(db->db_type == TSK_HDB_DBTYPE_MD5SUM_ID) {
        TSNPRINTF(idx_type, 1024, _TSK_T("%") PRIcTSK, TSK_HDB_DBTYPE_MD5SUM_STR);
    }
    else if(db->db_type == TSK_HDB_DBTYPE_HK_ID) {
        TSNPRINTF(idx_type, 1024, _TSK_T("%") PRIcTSK, TSK_HDB_DBTYPE_HK_STR);
    }
    else if(db->db_type == TSK_HDB_DBTYPE_ENCASE_ID) {
        TSNPRINTF(idx_type, 1024, _TSK_T("%") PRIcTSK, TSK_HDB_DBTYPE_ENCASE_STR);
    }
    else {
        // The Java bindings only support the generation of md5 indexes for
        // an NSRL hash database.
        TSNPRINTF(idx_type, 1024, _TSK_T("%") PRIcTSK, TSK_HDB_DBTYPE_NSRL_MD5_STR);
    }
  
    if (tsk_hdb_make_index(db, idx_type) != 0) {
        setThrowTskCoreError(env, tsk_error_get_errstr());
    }
}

/*
 * Queries whether or not an index for MD5 look ups exists for a hash database.
 * @param env Pointer to Java environment from which this method was called.
 * @param obj The Java object from which this method was called.
 * @param dbHandle A handle for the hash database.
 * @return True if the index exists.
 */
JNIEXPORT jboolean JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_hashDbIndexExistsNat
  (JNIEnv * env, jclass obj, jint dbHandle) {
    if((size_t)dbHandle > hashDbs.size()) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    TSK_HDB_INFO *db = hashDbs.at(dbHandle-1);
    if (db == NULL) {
        setThrowTskCoreError(env, "Invalid database handle");
        return (jboolean)false;
    }

    return (jboolean)(db->has_index(db, TSK_HDB_HTYPE_MD5_ID) == 1);
}

/*
 * Query and get size of the device (such as physical disk, or image) pointed by the path
 * Might require elevated priviletes to work (otherwise will error)
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param devPathJ the device path
 * @return size of device, set throw jni exception on error
 */
JNIEXPORT jlong JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_findDeviceSizeNat
  (JNIEnv * env, jclass obj, jstring devPathJ) {
     
      jlong devSize = 0;
      const char* devPath = env->GetStringUTFChars(devPathJ, 0);

      // open the image to get the size
      TSK_IMG_INFO * img_info =
        tsk_img_open_utf8_sing(devPath, TSK_IMG_TYPE_DETECT, 0);
      if (img_info == NULL) {
        setThrowTskCoreError(env, tsk_error_get());
        env->ReleaseStringUTFChars(devPathJ , devPath); 
        return -1;
      }

      TSK_OFF_T imgSize = img_info->size;
      devSize = imgSize;

      //cleanup
      tsk_img_close(img_info);
      env->ReleaseStringUTFChars(devPathJ , devPath); 

      return devSize;
}

/*
 * Test whether an image is supported
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param imagePathJ the image path
 * @return true if the image can be processed, false otherwise
 */
JNIEXPORT jboolean JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_isImageSupportedNat
  (JNIEnv * env, jclass obj, jstring imagePathJ) {
      
    TskIsImageSupported tskIsImage;
    TSK_TCHAR imagePathT[1024];
    toTCHAR(env, imagePathT, 1024, imagePathJ);

    // It seems like passing &imagePathT should work instead of making this new array,
    // but it generated an EXCEPTION_ACCESS_VIOLATION during testing.
    TSK_TCHAR ** imagePaths = (TSK_TCHAR**)tsk_malloc((1) * sizeof(TSK_TCHAR*));
    bool result;
    imagePaths[0] = imagePathT;
    if (tskIsImage.openImage(1, imagePaths, TSK_IMG_TYPE_DETECT, 0)) {
        result = false;
    } else {
        if (tskIsImage.findFilesInImg()) {
            result = false;
        } else {
            if (tskIsImage.isImageSupported()) {
                result = true;
            }
            else {
                result = false;
            }   
        }
    }

    // Cleanup
    free(imagePaths);

    return (jboolean) result;
}

/*
* Returns the current Sleuthkit version as a long
* @return the current version
*/
JNIEXPORT jlong JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_getSleuthkitVersionNat
(JNIEnv * env, jclass obj) {
    return (jlong)TSK_VERSION_NUM;
}


/*
 * Finish the image being created by image writer.
 * @param env pointer to java environment this was called from
 * @param obj the java object this was called from
 * @param a_img_info the image info pointer
 */
JNIEXPORT jint JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_finishImageWriterNat
(JNIEnv * env, jclass obj, jlong a_img_info) {
    // Set up the TSK_IMG_INFO object
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    return tsk_img_writer_finish(img_info);
}

/*
 * Get the progess of the finishImage process as an integer from 0 to 100
 */
JNIEXPORT jint JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_getFinishImageProgressNat
(JNIEnv * env, jclass obj, jlong a_img_info) {
    // Set up the TSK_IMG_INFO object
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    IMG_RAW_INFO *raw_info = (IMG_RAW_INFO*)img_info;

    if (raw_info->img_writer != NULL) {
        return (raw_info->img_writer->finishProgress);
    }
    return 0;

}

/*
* Cancel the finishImage process
*/
JNIEXPORT void JNICALL Java_org_sleuthkit_datamodel_SleuthkitJNI_cancelFinishImageNat
(JNIEnv * env, jclass obj, jlong a_img_info) {
    // Set up the TSK_IMG_INFO object
    TSK_IMG_INFO *img_info = castImgInfo(env, a_img_info);
    IMG_RAW_INFO *raw_info = (IMG_RAW_INFO*)img_info;

    if (raw_info->img_writer != NULL) {
        raw_info->img_writer->cancelFinish = 1;
    }
    return ;
}
