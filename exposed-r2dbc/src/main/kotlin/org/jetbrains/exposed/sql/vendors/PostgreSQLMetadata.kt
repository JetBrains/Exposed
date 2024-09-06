package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel

open class PostgreSQLMetadata : MetadataProvider {
    override fun identifierQuoteString(): String = "\""

    override fun storesUpperCaseIdentifiers(): Boolean = false

    override fun storesUpperCaseQuotedIdentifiers(): Boolean = false

    override fun storesLowerCaseIdentifiers(): Boolean = true

    override fun storesLowerCaseQuotedIdentifiers(): Boolean = false

    override fun supportsMixedCaseIdentifiers(): Boolean = false

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean = true

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

    override fun extraNameCharacters(): String = ""

    override fun maxColumnNameLength(): Int = 63

    override fun supportsAlterTableWithAddColumn(): Boolean = true

    override fun supportsMultipleResultSets(): Boolean = true

    override fun supportsSelectForUpdate(): Boolean = true

    override fun getDefaultTransactionIsolation(): IsolationLevel = IsolationLevel.READ_COMMITTED

    // QUERY SQL

    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        TODO("Not yet implemented")
    }

    override fun setCatalog(value: String): String {
        return ""
    }

    override fun getSchema(): String {
        return "SELECT current_schema()"
    }

    override fun setSchema(value: String): String {
        return "SET SESSION search_path TO '$value'"
    }

    override fun getCatalogs(): String {
        return "SELECT datname AS TABLE_CAT FROM pg_catalog.pg_database WHERE datallowconn = true ORDER BY datname"
    }

    override fun getSchemas(): String {
        return buildString {
            append("SELECT nspname AS TABLE_SCHEM FROM pg_catalog.pg_namespace ")
            append("WHERE nspname <> 'pg_toast' AND (nspname !~ '^pg_temp_' OR nspname = (pg_catalog.current_schemas(true))[1]) ")
            append("AND (nspname !~ '^pg_toast_temp_' OR nspname = replace((pg_catalog.current_schemas(true))[1], 'pg_temp_', 'pg_toast_temp_')) ")
            append("ORDER BY TABLE_SCHEM")
        }
    }

    override fun getReadOnlyMode(): String {
        TODO("Not yet implemented")
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String?, type: String): String {
        return buildString {
            append("SELECT NULL AS TABLE_CAT, n.nspname AS TABLE_SCHEM, c.relname AS TABLE_NAME ")
            append("FROM pg_catalog.pg_namespace n, pg_catalog.pg_class c WHERE c.relnamespace = n.oid ")
            if (!schemaPattern.isNullOrEmpty()) {
                append("AND n.nspname LIKE '$schemaPattern' ")
            }
            if (!tableNamePattern.isNullOrEmpty()) {
                append("AND c.relname LIKE '$tableNamePattern' ")
            }
            append("AND (false ")
            when (type) {
                "TABLE" -> append("OR (c.relkind = 'r' AND n.nspname !~ '^pg_' AND n.nspname <> 'information_schema') ")
                "SEQUENCE" -> append("OR (c.relkind = 'S') ")
            }
            append(") ORDER BY TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        return buildString {
            append("SELECT * FROM ( ")
            append("SELECT a.attname, a.atttypid, a.attnotnull OR (t.typtype = 'd' AND t.typnotnull) AS attnotnull, a.atttypmod, ")
            append("nullif(a.attidentity, '') as attidentity, ")
            append("pg_catalog.pg_get_expr(def.adbin, def.adrelid) AS adsrc, t.typbasetype, t.typtype, t.typtypmod ")
            append("FROM pg_catalog.pg_namespace n ")
            append("JOIN pg_catalog.pg_class c ON (c.relnamespace=n.oid) ")
            append("JOIN pg_catalog.pg_attribute a ON (a.attrelid=c.oid) ")
            append("JOIN pg_catalog.pg_type t ON (a.atttypid=t.oid) ")
            append("LEFT JOIN pg_catalog.pg_attrdef def ON (a.attrelid=def.adrelid AND a.attnum = def.adnum) ")
            append("WHERE c.relkind in ('r','p','v','f','m') and a.attnum > 0 AND NOT a.attisdropped ")
            append("AND n.nspname LIKE $schemaPattern ")
            append("AND c.relname LIKE $tableNamePattern ")
            append(") c WHERE true ")
            append("AND attname LIKE % ")
            append("ORDER BY nspname, c.relname")
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
            append("WHERE true ")
            append("AND n.nspname = $schema ")
            append("AND ct.relname LIKE $table ")
            append("AND i.indisprimary")
        }
        return buildString {
            append("SELECT result.COLUMN_NAME, result.PK_NAME ")
            append("FROM ($sub) result ")
            append("WHERE result.A_ATTNUM = (result.KEYS).x ORDER BY result.table_name, result.pk_name")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        val sub = buildString {
            append("SELECT NOT i.indisunique AS NON_UNIQUE, ci.relname AS INDEX_NAME, ci.oid AS CI_OID, ")
            append("(information_schema._pg_expandarray(i.indkey)).n AS ORDINAL_POSITION, pg_catalog.pg_get_expr(i.indpred, i.indrelid) AS FILTER_CONDITION, ")
            append("FROM pg_catalog.pg_class ct ")
            append("JOIN pg_catalog.pg_namespace n ON (ct.relnamespace = n.oid) ")
            append("JOIN pg_catalog.pg_index i ON (ct.oid = i.indrelid) ")
            append("JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid) ")
            append("WHERE true ")
            append("AND n.nspname = $schema ")
            append("AND ct.relname LIKE $table")
        }
        return buildString {
            append("SELECT tmp.NON_UNIQUE, tmp.INDEX_NAME, ")
            append("trim(both '\"' from pg_catalog.pg_get_indexdef(tmp.CI_OID, tmp.ORDINAL_POSITION, false)) AS COLUMN_NAME, tmp.FILTER_CONDITION ")
            append("FROM ($sub) tmp ORDER BY NON_UNIQUE, INDEX_NAME")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        // this needs to be run first? so needs to use an alternate setup
//        val maxKeys = "SELECT setting FROM pg_catalog.pg_settings WHERE name='max_index_keys'"
        return buildString {
            append("SELECT pkc.relname AS PKTABLE_NAME, pka.attname AS PKCOLUMN_NAME, ")
            append("fkc.relname AS FKTABLE_NAME, fka.attname AS FKCOLUMN_NAME, con.conname AS FK_NAME, ")
            append("CASE con.confupdtype ")
            append("WHEN 'c' THEN 1 ")
            append("WHEN 'n' THEN 2 ")
            append("WHEN 'd' THEN 3 ")
            append("WHEN 'r' THEN 4 ")
            append("WHEN 'p' THEN 5 ")
            append("WHEN 'a' THEN 6 ")
            append("ELSE NULL END AS UPDATE_RULE, ")
            append("CASE con.confdeltype ")
            append("WHEN 'c' THEN 1 ")
            append("WHEN 'n' THEN 2 ")
            append("WHEN 'd' THEN 3 ")
            append("WHEN 'r' THEN 4 ")
            append("WHEN 'p' THEN 5 ")
            append("WHEN 'a' THEN 6 ")
            append("ELSE NULL END AS DELETE_RULE, ")
            append("FROM pg_catalog.pg_class pkc, pg_catalog.pg_attribute pka, ")
            append("pg_catalog.pg_namespace fkn, pg_catalog.pg_class fkc, pg_catalog.pg_attribute fka, ")
            append("pg_catalog.pg_constraint con, pg_catalog.generate_series(1, 10) pos(n), pg_catalog.pg_class pkic ")
            append("WHERE pka.attnum = con.confkey[pos.n] AND con.confrelid = pkc.oid ")
            append("AND fkn.oid = fkc.relnamespace AND fkc.oid = fka.attrelid AND fka.attnum = con.conkey[pos.n] ")
            append("AND con.conrelid = fkc.oid AND con.contype = 'f' ")
            append("AND pkic.relkind = 'i' AND pkic.oid = con.conindid ")
            append("AND fkn.nspname = $schema ")
            append("AND fkc.relname = $table ")
            append("ORDER BY pkn.nspname,pkc.relname, con.conname,pos.n")
        }
    }
}
