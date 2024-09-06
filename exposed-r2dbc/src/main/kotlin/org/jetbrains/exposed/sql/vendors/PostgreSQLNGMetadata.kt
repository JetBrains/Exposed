package org.jetbrains.exposed.sql.vendors

class PostgreSQLNGMetadata : PostgreSQLMetadata() {
    override fun sqlKeywords(): String {
        return "abort,acl,add,aggregate,append,archive,arch_store,backward,binary,boolean,change,cluster,copy,database,delimiter," +
            "delimiters,do,extend,explain,forward,heavy,index,inherits,isnull,light,listen,load,merge,nothing,notify,notnull,oids,purge," +
            "rename,replace,retrieve,returns,rule,recipe,setof,stdin,stdout,store,vacuum,verbose,version"
    }

    override fun maxColumnNameLength(): Int = 64
}
