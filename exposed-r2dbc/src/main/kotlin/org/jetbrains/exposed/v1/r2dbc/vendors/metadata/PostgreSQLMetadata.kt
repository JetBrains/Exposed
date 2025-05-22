package org.jetbrains.exposed.v1.r2dbc.vendors.metadata

import org.jetbrains.exposed.v1.core.ReferenceOption

@Suppress("MagicNumber")
internal object PostgreSQLPropertyProvider : PropertyProvider() {
    override val storesLowerCaseIdentifiers: Boolean
        get() = true

    override val supportsLimitWithUpdateOrDelete: Boolean
        get() = false

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
internal object PostgreSQLTypeProvider : SqlTypeProvider() {
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
internal class PostgreSQLMetadata : MetadataProvider(PostgreSQLPropertyProvider, PostgreSQLTypeProvider) {
    override fun getUsername(): String {
        return "SELECT current_user AS USER_NAME"
    }

    override fun getReadOnlyMode(): String {
        return "SELECT current_setting('transaction_read_only') AS READ_ONLY"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        return if (value) {
            "SET transaction_read_only TO on"
        } else {
            "SET transaction_read_only TO off"
        }
    }

    override fun getDatabaseMode(): String = ""

    override fun getCatalog(): String {
        return "SELECT current_database() AS TABLE_CAT"
    }

    override fun setCatalog(value: String): String = ""

    override fun getSchema(): String {
        return "SELECT current_schema() AS TABLE_SCHEM"
    }

    override fun getCatalogs(): String = ""

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

    override fun getTables(catalog: String, schemaPattern: String): String {
        return buildString {
            append("SELECT current_database() AS TABLE_CAT, n.nspname AS TABLE_SCHEM, c.relname AS TABLE_NAME ")
            append("FROM pg_catalog.pg_namespace AS n, pg_catalog.pg_class AS c ")
            append("WHERE c.relnamespace = n.oid ")
            append("AND current_database() = '$catalog'")
            append("AND n.nspname LIKE '$schemaPattern' ")
            append("AND c.relkind = 'r' AND n.nspname !~ '^pg_' AND n.nspname <> 'information_schema' ")
            append("ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getAllSequences(): String {
        return buildString {
            append("SELECT c.relname AS SEQUENCE_NAME ")
            append("FROM pg_catalog.pg_namespace AS n, pg_catalog.pg_class AS c ")
            append("WHERE c.relnamespace = n.oid ")
            append("AND c.relkind = 'S' ")
            append("ORDER BY SEQUENCE_NAME")
        }
    }

    override fun getSequences(catalog: String, schemaPattern: String, table: String): String {
        val sub = buildString {
            append("SELECT ci.relname AS SEQUENCE_NAME, seq.seqstart AS SEQUENCE_START, seq.seqincrement AS SEQUENCE_INCREMENT, ")
            append("seq.seqmax AS SEQUENCE_MAX, seq.seqmin AS SEQUENCE_MIN, seq.seqcache AS SEQUENCE_CACHE, ")
            append("seq.seqcycle AS SEQUENCE_CYCLE, ci.oid AS SEQUENCE_ID ")
            append("FROM pg_catalog.pg_sequence AS seq ")
            append("JOIN pg_catalog.pg_class AS ci ON (ci.oid = seq.seqrelid AND ci.relkind = 'S') ")
            append("JOIN pg_catalog.pg_namespace AS ni ON (ci.relnamespace = ni.oid) ")
            append("WHERE ni.nspname LIKE '$schemaPattern'")
        }
        return buildString {
            append("SELECT sd.SEQUENCE_NAME, sd.SEQUENCE_START, sd.SEQUENCE_INCREMENT, ")
            append("sd.SEQUENCE_MAX, sd.SEQUENCE_MIN, sd.SEQUENCE_CACHE, sd.SEQUENCE_CYCLE ")
            append("FROM pg_catalog.pg_namespace AS nt ")
            append("INNER JOIN pg_catalog.pg_class AS ct ON (nt.oid = ct.relnamespace AND ct.relkind IN ('p', 'r')) ")
            append("INNER JOIN pg_catalog.pg_depend AS d ON (ct.oid = d.refobjid) ")
            append("LEFT OUTER JOIN ($sub) AS sd ON (sd.SEQUENCE_ID = d.objid) ")
            append("WHERE nt.nspname LIKE '$schemaPattern' ")
            append("AND ct.relname = '$table' ")
            append("ORDER BY sd.SEQUENCE_NAME")
        }
    }

    override fun getColumns(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT COLUMN_NAME, ")
            typeProvider.appendDataTypes("DATA_TYPE", "DATA_TYPE", this)
            append(", ")
            typeProvider.appendDataPrecisions("DATA_TYPE", "COLUMN_SIZE", this)
            append(", ")
            append("NUMERIC_SCALE AS DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN 'TRUE' ELSE 'FALSE' END AS NULLABLE, ")
            append("COLUMN_DEFAULT AS COLUMN_DEF, ORDINAL_POSITION, ")
            append("CASE WHEN IS_IDENTITY = 'YES' OR COLUMN_DEFAULT LIKE 'nextval(%' THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_CATALOG = '$catalog' ")
            append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schemaPattern: String, table: String): String {
        val sub = buildString {
            append("SELECT ct.relname AS TABLE_NAME, a.attname AS COLUMN_NAME, ci.relname AS PK_NAME, ")
            append("information_schema._pg_expandarray(i.indkey) AS KEYS, a.attnum AS A_ATTNUM ")
            append("FROM pg_catalog.pg_class AS ct ")
            append("JOIN pg_catalog.pg_attribute AS a ON (ct.oid = a.attrelid) ")
            append("JOIN pg_catalog.pg_namespace AS n ON (ct.relnamespace = n.oid) ")
            append("JOIN pg_catalog.pg_index AS i ON (a.attrelid = i.indrelid) ")
            append("JOIN pg_catalog.pg_class AS ci ON (ci.oid = i.indexrelid) ")
            append("WHERE current_database() = '$catalog'")
            append("AND n.nspname LIKE '$schemaPattern' ")
            append("AND ct.relname = '$table' ")
            append("AND i.indisprimary")
        }
        return buildString {
            append("SELECT result.COLUMN_NAME, result.PK_NAME ")
            append("FROM ($sub) AS result ")
            append("WHERE result.A_ATTNUM = (result.KEYS).x ")
            append("ORDER BY result.COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schemaPattern: String, table: String): String {
        val sub = buildString {
            append("SELECT NOT i.indisunique AS NON_UNIQUE, ci.relname AS INDEX_NAME, ")
            append("(information_schema._pg_expandarray(i.indkey)).n AS ORDINAL_POSITION, ")
            append("pg_catalog.pg_get_expr(i.indpred, i.indrelid) AS FILTER_CONDITION, ")
            append("ci.oid AS CI_OID ")
            append("FROM pg_catalog.pg_class AS ct ")
            append("JOIN pg_catalog.pg_namespace AS n ON (ct.relnamespace = n.oid) ")
            append("JOIN pg_catalog.pg_index AS i ON (ct.oid = i.indrelid) ")
            append("JOIN pg_catalog.pg_class AS ci ON (ci.oid = i.indexrelid) ")
            append("WHERE current_database() = '$catalog' ")
            append("AND n.nspname LIKE '$schemaPattern' ")
            append("AND ct.relname = '$table'")
        }
        return buildString {
            append("SELECT result.NON_UNIQUE, result.INDEX_NAME, result.ORDINAL_POSITION, ")
            append("trim(both '\"' from pg_catalog.pg_get_indexdef(result.CI_OID, result.ORDINAL_POSITION, false)) AS COLUMN_NAME, ")
            append("result.FILTER_CONDITION ")
            append("FROM ($sub) AS result ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT pkc.relname AS PKTABLE_NAME, pka.attname AS PKCOLUMN_NAME, ")
            append("fkc.relname AS FKTABLE_NAME, fka.attname AS FKCOLUMN_NAME, ")
            append("pos.n AS KEY_SEQ, ")
            typeProvider.appendReferenceOptions("con.confupdtype", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("con.confdeltype", "DELETE_RULE", this)
            append(", ")
            append("con.conname AS FK_NAME ")
            append("FROM pg_catalog.pg_namespace AS pkn, pg_catalog.pg_class AS pkc, pg_catalog.pg_attribute AS pka, ")
            append("pg_catalog.pg_namespace AS fkn, pg_catalog.pg_class AS fkc, pg_catalog.pg_attribute AS fka, ")
            append("pg_catalog.pg_constraint AS con, pg_catalog.generate_series(1, 10) AS pos(n), pg_catalog.pg_class AS pkic ")
            append("WHERE pkn.oid = pkc.relnamespace AND pkc.oid = pka.attrelid AND pka.attnum = con.confkey[pos.n] ")
            append("AND con.confrelid = pkc.oid AND fkn.oid = fkc.relnamespace AND fkc.oid = fka.attrelid ")
            append("AND fka.attnum = con.conkey[pos.n] AND con.conrelid = fkc.oid AND con.contype = 'f' ")
            append("AND pkic.relkind = 'i' AND pkic.oid = con.conindid ")
            append("AND fkn.nspname LIKE '$schemaPattern' ")
            append("AND fkc.relname = '$table' ")
            append("ORDER BY PKTABLE_NAME, KEY_SEQ")
        }
    }

    override fun getCheckConstraints(catalog: String, schemaPattern: String, table: String): String = ""
}
