#ifndef RESOURCE_ARCHIVE_H
#define RESOURCE_ARCHIVE_H

/*
 Resource archive file format
 - All meta-data in network endian
 - All entries are lexically sorted by name

 uint32_t m_Version;     // No minor or bug-fix version numbering. Version must match identically.
 uint32_t m_Pad;
 uint64_t m_Userdata;    // For run-time use
 uint32_t m_StringPoolOffset;
 uint32_t m_StringPoolSize;
 uint32_t m_EntryCount;
 uint32_t m_EntryOffset; // Offset to first entry relative file start

 struct Entry
 {
     uint32_t m_NameOffset;     // Offset to name relative file start
     uint32_t m_ResourceOffset; // Offset to resource relative file start
     uint32_t m_ResourceSize;
     // 0xFFFFFFFF if uncompressed
     uint32_t m_ResourceCompressedSize;
 };

*/

namespace dmResourceArchive
{
    typedef struct Archive* HArchive;

    enum Result
    {
        RESULT_OK = 0,
        RESULT_NOT_FOUND = 1,
        RESULT_VERSION_MISMATCH = -1,
        RESULT_IO_ERROR = -2,
        RESULT_MEM_ERROR = -3,
        RESULT_OUTBUFFER_TOO_SMALL = -4,
        RESULT_UNKNOWN = -1000,
    };

    struct EntryInfo
    {
        const char* m_Name;
        uint32_t    m_Size;
        // 0xFFFFFFFF if uncompressed
        uint32_t    m_CompressedSize;
        uint32_t    m_Offset; // For internal use
        uint32_t    m_Flags;  // For internal use
        void*       m_Entry;  // For internal use
    };

    /**
     * Wrap an archive already loaded in memory. Call delete Delete() on wrapped
     * archives is not necessary
     * @param buffer archive in memory to wrap
     * @param buffer_size archive size
     * @param archive archive handle
     * @return RESULT_OK on success
     */
    Result WrapArchiveBuffer(const void* buffer, uint32_t buffer_size, HArchive* archive);

    /**
     * Load archive from filename. Only the metadata is loaded into memory.
     * Resources are loaded on-demand using the Read() function
     * @param file_name archive to load
     * @param archive archive handle
     * @return RESULT_OK on success
     */
    Result LoadArchive(const char* file_name, HArchive* archive);

    /**
     * Find file within archive
     * @note Filenames must be on a normalized and canonical form, i.e. no duplicated slashes, .. or . in path
     * @param archive archive handle
     * @param name file-name to find .
     * @param entry entry info
     * @return RESULT_OK on success
     */
    Result FindEntry(HArchive archive, const char* name, EntryInfo* entry);

    /**
     * Read resource
     * @param archive archive handle
     * @param entry_info entry info
     * @param buffer buffer to load to
     * @return RESULT_OK on success
     */
    Result Read(HArchive archive, EntryInfo* entry_info, void* buffer);

    /**
     * Delete archive. Only required for archives created with the LoadArchive function
     * @param archive archive handle
     */
    void Delete(HArchive archive);

    /**
     * Get total entries, i.e. files/resources, in archive
     * @param archive archive handle
     * @return entry count
     */
    uint32_t GetEntryCount(HArchive archive);

}  // namespace dmResourceArchive

#endif
