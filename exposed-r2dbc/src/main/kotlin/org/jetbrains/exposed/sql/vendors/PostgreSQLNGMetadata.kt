package org.jetbrains.exposed.sql.vendors

@Suppress("MagicNumber")
internal object PostgreSQLNGPropertyProvider : PostgreSQLPropertyProvider() {
    override val maxColumnNameLength: Int
        get() = 64

    override fun sqlKeywords(): String {
        return "abort,acl,add,aggregate,append,archive,arch_store,backward,binary,boolean,change,cluster,copy,database,delimiter," +
            "delimiters,do,extend,explain,forward,heavy,index,inherits,isnull,light,listen,load,merge,nothing,notify,notnull,oids,purge," +
            "rename,replace,retrieve,returns,rule,recipe,setof,stdin,stdout,store,vacuum,verbose,version"
    }
}

@Suppress("MagicNumber")
internal object PostgreSQLNGTypeProvider : PostgreSQLTypeProvider() {
    override val maxPrecision: String
        get() = "-1"

    override val booleanType: DataType
        get() = super.booleanType.copy(code = 16)

    override val additionalTypes: Set<DataType>
        get() {
            val toReplace = setOf(
                DataType("BPCHAR", 12, characterPrecision),
                DataType("JSON", 12, maxPrecision),
                DataType("JSONB", 12, maxPrecision),
                DataType("OID", 4, numericPrecision),
                DataType("TIMESTAMPTZ", 2014, "35"),
                DataType("UUID", 1111, "36")
            )
            return super.additionalTypes - toReplace + toReplace
        }
}

class PostgreSQLNGMetadata : PostgreSQLMetadata() {
    override val propertyProvider: PropertyProvider = PostgreSQLNGPropertyProvider

    override val typeProvider: SqlTypeProvider = PostgreSQLNGTypeProvider

    override fun getCatalog(): String {
        return "SELECT NULL AS TABLE_CAT"
    }

    override fun setSchema(value: String): String {
        return "SET SCHEMA '$value'"
    }

    override fun getCatalogs(): String {
        return "SELECT NULL AS TABLE_CAT"
    }
}
