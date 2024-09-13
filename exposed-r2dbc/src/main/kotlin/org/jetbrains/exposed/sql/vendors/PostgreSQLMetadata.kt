package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.ReferenceOption

@Suppress("MagicNumber")
internal open class PostgreSQLPropertyProvider : PropertyProvider() {
    override val storesLowerCaseIdentifiers: Boolean
        get() = true

    override val maxColumnNameLength: Int
        get() = 63

    override fun sqlKeywords(): String {
        return "abort,absent,access,aggregate,also,analyse,analyze,attach,backward,bit,cache,checkpoint,class,cluster,columns,comment," +
            "comments,compression,concurrently,configuration,conflict,connection,content,conversion,copy,cost,csv,current_catalog," +
            "current_schema,database,delimiter,delimiters,depends,detach,dictionary,disable,discard,do,document,enable,encoding," +
            "encrypted,enum,event,exclusive,explain,expression,extension,family,finalize,force,format,forward,freeze,functions,generated," +
            "greatest,groups,handler,header,if,ilike,immutable,implicit,import,include,indent,index,indexes,inherit,inherits,inline," +
            "instead,isnull,json,json_array,json_arrayagg,json_object,json_objectagg,keys,label,leakproof,least,limit,listen,load,location,lock," +
            "locked,logged,mapping,materialized,mode,move,nfc,nfd,nfkc,nfkd,nothing,notify,notnull,nowait,off,offset,oids,operator,owned,owner," +
            "parallel,parser,passing,password,plans,policy,prepared,procedural,procedures,program,publication,quote,reassign,recheck,refresh," +
            "reindex,rename,replace,replica,reset,restrict,returning,routines,rule,scalar,schemas,sequences,server,setof,share,show,skip,snapshot," +
            "stable,standalone,statistics,stdin,stdout,storage,stored,strict,strip,subscription,support,sysid,tables,tablespace,temp,template," +
            "text,truncate,trusted,types,unencrypted,unlisten,unlogged,until,vacuum,valid,validate,validator,variadic,verbose,version,views," +
            "volatile,whitespace,wrapper,xml,xmlattributes,xmlconcat,xmlelement,xmlexists,xmlforest,xmlnamespaces,xmlparse,xmlpi,xmlroot," +
            "xmlserialize,xmltable,yes"
    }
}

@Suppress("MagicNumber")
internal open class PostgreSQLTypeProvider : SqlTypeProvider() {
    override val referenceOptions: Map<ReferenceOption, Int> by lazy {
        mapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.RESTRICT to 1,
            ReferenceOption.NO_ACTION to 3,
            ReferenceOption.SET_NULL to 2,
            ReferenceOption.SET_DEFAULT to 4
        )
    }

    // problematic: column size seems to be dependent on type stored in array (post-processing?)
    override val arrayType: DataType
        get() = super.arrayType.copy(precision = numericPrecision)

    // still haven't figured out the correct column to return accurate numeric sizes
    // the precision radix used by numeric_precision may need to be checked (requires post-query processing?)
    override val bigIntType: DataType
        get() = super.bigIntType.copy(precision = "19")

    override val booleanType: DataType
        get() = super.booleanType.copy(code = -7, precision = "1")

    override val dateType: DataType
        get() = super.dateType.copy(precision = "13")

    override val integerType: DataType
        get() = super.integerType.copy(precision = "10")

    // problematic: used by float and numeric_scale but does not return accurate scale...
    // same for additional DOUBLE PRECISION
    override val realType: DataType
        get() = super.realType.copy(precision = "8")

    override val smallIntType: DataType
        get() = super.smallIntType.copy(precision = "5")

    override val timeType: DataType
        get() = super.timeType.copy(precision = "15")

    override val additionalTypes: Set<DataType>
        get() = setOf(
            // array types might be a problem (requires post-result processing?)
            DataType("_INT4", 2003, "10"),
            DataType("_TEXT", 2003, maxPrecision),
            DataType("BPCHAR", 1, characterPrecision),
            DataType("BYTEA", -2, maxPrecision),
            DataType("CHARACTER", 1, characterPrecision),
            DataType("CHARACTER VARYING", 12, characterPrecision),
            DataType("DOUBLE PRECISION", 8, "17"),
            DataType("INT2", 5, "5"),
            DataType("INT4", 4, "10"),
            DataType("INT8", -5, "19"),
            DataType("FLOAT4", 7, "8"),
            DataType("FLOAT8", 8, "17"),
            DataType("JSON", 1111, maxPrecision),
            DataType("JSONB", 1111, maxPrecision),
            DataType("OID", -5, numericPrecision),
            DataType("TEXT", 12, maxPrecision),
            DataType("TIMESTAMPTZ", 93, "35"),
            DataType("UUID", 1111, maxPrecision),
        )

    override fun appendReferenceOptions(columnName: String, alias: String, stringBuilder: StringBuilder) {
        with(stringBuilder) {
            append("CASE $columnName ")
            referenceOptions.forEach { (option, value) ->
                val char = option.name.substringAfter('_').first().lowercase()
                append("WHEN '$char' THEN $value ")
            }
            append("ELSE NULL END $alias")
        }
    }
}

@Suppress("MagicNumber")
open class PostgreSQLMetadata : MetadataProvider(PostgreSQLPropertyProvider(), PostgreSQLTypeProvider()) {
    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getReadOnlyMode(): String {
        TODO("Not yet implemented")
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        return "SELECT current_database() AS TABLE_CAT"
    }

    // how to simulate doing nothing?
    override fun setCatalog(value: String): String {
        return ""
    }

    override fun getSchema(): String {
        return "SELECT current_schema() AS TABLE_SCHEM"
    }

    override fun setSchema(value: String): String {
        return "SET SESSION search_path TO '$value'"
    }

    override fun getCatalogs(): String {
        return buildString {
            append("SELECT datname AS TABLE_CAT ")
            append("FROM pg_catalog.pg_database ")
            append("WHERE datallowconn = true ")
            append("ORDER BY TABLE_CAT")
        }
    }

    override fun getSchemas(): String {
        return buildString {
            append("SELECT nspname AS TABLE_SCHEM ")
            append("FROM pg_catalog.pg_namespace ")
            append("WHERE nspname <> 'pg_toast' ")
            append("AND (nspname !~ '^pg_temp_' OR nspname = (pg_catalog.current_schemas(true))[1]) ")
            append("AND (nspname !~ '^pg_toast_temp_' ")
            append("OR nspname = replace((pg_catalog.current_schemas(true))[1], 'pg_temp_', 'pg_toast_temp_')) ")
            append("ORDER BY TABLE_SCHEM")
        }
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String): String {
        return buildString {
            append("SELECT NULL AS TABLE_CAT, n.nspname AS TABLE_SCHEM, c.relname AS TABLE_NAME ")
            append("FROM pg_catalog.pg_namespace n, pg_catalog.pg_class c ")
            append("WHERE c.relnamespace = n.oid ")
            if (!schemaPattern.isNullOrEmpty()) {
                append("AND n.nspname LIKE '$schemaPattern' ")
            }
            append("AND c.relname LIKE '$tableNamePattern' ")
            append("AND (false ")
            append("OR (c.relkind = 'r' AND n.nspname !~ '^pg_' AND n.nspname <> 'information_schema')) ")
            append("ORDER BY TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getSequences(): String {
        return buildString {
            append("SELECT c.relname AS SEQUENCE_NAME ")
            append("FROM pg_catalog.pg_namespace n, pg_catalog.pg_class c ")
            append("WHERE c.relnamespace = n.oid ")
            append("AND c.relkind = 'S' ")
            append("ORDER BY SEQUENCE_NAME")
        }
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_DEFAULT AS COLUMN_DEF, ")
            append("CASE WHEN IS_IDENTITY = 'YES' OR COLUMN_DEFAULT LIKE 'nextval(%' THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT, ")
            append("NUMERIC_SCALE AS DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END AS NULLABLE, ")
            typeProvider.appendDataPrecisions("DATA_TYPE", "COLUMN_SIZE", this)
            append(", ")
            typeProvider.appendDataTypes("DATA_TYPE", "DATA_TYPE", this)
            append(" ")
            append("FROM INFORMATION_SCHEMA.COLUMNS WHERE ")
            append("TABLE_NAME LIKE '$tableNamePattern' ")
            if (catalog.isNotEmpty()) {
                append("AND TABLE_CATALOG LIKE '$catalog' ")
            }
            if (schemaPattern.isNotEmpty()) {
                append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            }
            append("AND COLUMN_NAME LIKE '%' ")
            append("ORDER BY TABLE_NAME, ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        val sub = buildString {
            append("SELECT ct.relname AS TABLE_NAME, a.attname AS COLUMN_NAME, ci.relname AS PK_NAME, ")
            append("information_schema._pg_expandarray(i.indkey) AS KEYS, a.attnum AS A_ATTNUM ")
            append("FROM pg_catalog.pg_class ct ")
            append("JOIN pg_catalog.pg_attribute a ON (ct.oid = a.attrelid) ")
            append("JOIN pg_catalog.pg_namespace n ON (ct.relnamespace = n.oid) ")
            append("JOIN pg_catalog.pg_index i ON ( a.attrelid = i.indrelid) ")
            append("JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid) ")
            append("WHERE n.nspname = '$schema' ")
            append("AND ct.relname LIKE '$table' ")
            append("AND i.indisprimary")
        }
        return buildString {
            append("SELECT result.COLUMN_NAME, result.PK_NAME ")
            append("FROM ($sub) result ")
            append("WHERE result.A_ATTNUM = (result.KEYS).x ")
            append("ORDER BY result.COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        val sub = buildString {
            append("SELECT NOT i.indisunique AS NON_UNIQUE, ci.relname AS INDEX_NAME, ci.oid AS CI_OID, ")
            append("(information_schema._pg_expandarray(i.indkey)).n AS ORDINAL_POSITION, ")
            append("pg_catalog.pg_get_expr(i.indpred, i.indrelid) AS FILTER_CONDITION ")
            append("FROM pg_catalog.pg_class ct ")
            append("JOIN pg_catalog.pg_namespace n ON (ct.relnamespace = n.oid) ")
            append("JOIN pg_catalog.pg_index i ON (ct.oid = i.indrelid) ")
            append("JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid) ")
            append("WHERE n.nspname = '$schema' ")
            append("AND ct.relname LIKE '$table'")
        }
        return buildString {
            append("SELECT tmp.NON_UNIQUE, tmp.INDEX_NAME, tmp.FILTER_CONDITION, ")
            append("trim(both '\"' from pg_catalog.pg_get_indexdef(tmp.CI_OID, tmp.ORDINAL_POSITION, false)) AS COLUMN_NAME ")
            append("FROM ($sub) tmp ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT pkn.nspname AS PKTABLE_SCHEM, pkc.relname AS PKTABLE_NAME, pka.attname AS PKCOLUMN_NAME, ")
            append("fkc.relname AS FKTABLE_NAME, fka.attname AS FKCOLUMN_NAME, con.conname AS FK_NAME, ")
            append("pos.n AS KEY_SEQ, ")
            typeProvider.appendReferenceOptions("con.confupdtype", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("con.confdeltype", "DELETE_RULE", this)
            append(" ")
            append("FROM pg_catalog.pg_namespace pkn, pg_catalog.pg_class pkc, pg_catalog.pg_attribute pka, ")
            append("pg_catalog.pg_namespace fkn, pg_catalog.pg_class fkc, pg_catalog.pg_attribute fka, ")
            append("pg_catalog.pg_constraint con, pg_catalog.generate_series(1, 10) pos(n), pg_catalog.pg_class pkic ")
            append("WHERE pkn.oid = pkc.relnamespace AND pkc.oid = pka.attrelid AND pka.attnum = con.confkey[pos.n] ")
            append("AND con.confrelid = pkc.oid AND fkn.oid = fkc.relnamespace AND fkc.oid = fka.attrelid ")
            append("AND fka.attnum = con.conkey[pos.n] AND con.conrelid = fkc.oid AND con.contype = 'f' ")
            append("AND pkic.relkind = 'i' AND pkic.oid = con.conindid ")
            append("AND fkn.nspname = '$schema' ")
            append("AND fkc.relname = '$table' ")
            append("ORDER BY PKTABLE_SCHEM, PKTABLE_NAME, FK_NAME, KEY_SEQ")
        }
    }
}
