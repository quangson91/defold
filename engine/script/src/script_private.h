#ifndef SCRIPT_PRIVATE_H
#define SCRIPT_PRIVATE_H

#include <dlib/hashtable.h>

#define SCRIPT_CONTEXT "__script_context"
#define SCRIPT_MAIN_THREAD "__script_main_thread"
#define SCRIPT_ERROR_HANDLER_VAR "__error_handler"

namespace dmScript
{
    struct Module
    {
        char*       m_Script;
        uint32_t    m_ScriptSize;
        char*       m_Name;
        void*       m_Resource;
    };

    typedef struct ScriptExtension* HScriptExtension;

    struct Context
    {
        dmConfigFile::HConfig       m_ConfigFile;
        dmResource::HFactory        m_ResourceFactory;
        dmHashTable64<Module>       m_Modules;
        dmHashTable64<Module*>      m_PathToModule;
        dmHashTable64<int>          m_HashInstances;
        dmArray<HScriptExtension>   m_ScriptExtensions;
        lua_State*                  m_LuaState;
        int                         m_ContextTableRef;
        bool                        m_EnableExtensions;
    };

    bool ResolvePath(lua_State* L, const char* path, uint32_t path_size, dmhash_t& out_hash);

    bool GetURL(lua_State* L, dmMessage::URL& out_url);

    bool GetUserData(lua_State* L, uintptr_t& out_user_data, const char* user_type);

    bool IsValidInstance(lua_State* L);

    /**
     * Remove all modules.
     * @param context script context
     */
    void ClearModules(HContext context);

    // Exposed here for tests in test_script_module.cpp
    const char* FindSuitableChunkname(const char* input);
    const char* PrefixFilename(const char *input, char prefix, char *buf, uint32_t size);
}

#endif // SCRIPT_PRIVATE_H
