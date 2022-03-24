package org.jetbrains.exposed.sql.vendors.db2compat

import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.*
import java.sql.Date
import java.util.*

/**
 * @author : youhuajie
 * @since : 2022/3/20
 * @description:
 **/

class DB2ResultSet(val resultSet: Array<ResultSet>): ResultSet {

    var index = 0
    var current = resultSet[0]

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        return current.unwrap(iface)
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return current.isWrapperFor(iface)
    }

    override fun close() {
        resultSet.forEach { it.close() }
    }

    override fun next(): Boolean {
        if (current.next()) {
            return true
        } else {
            index++
            return if (index == resultSet.size) {
                false
            } else {
                current = resultSet[index]
                return current.next()
            }
        }
    }

    override fun wasNull(): Boolean {
        return current.wasNull()
    }

    override fun getString(columnIndex: Int): String {
        return current.getString(columnIndex)
    }

    override fun getString(columnLabel: String?): String {
        return current.getString(columnLabel)
    }

    override fun getBoolean(columnIndex: Int): Boolean {
        return current.getBoolean(columnIndex)
    }

    override fun getBoolean(columnLabel: String?): Boolean {
        return current.getBoolean(columnLabel)
    }

    override fun getByte(columnIndex: Int): Byte {
        return current.getByte(columnIndex)
    }

    override fun getByte(columnLabel: String?): Byte {
        return current.getByte(columnLabel)
    }

    override fun getShort(columnIndex: Int): Short {
        return current.getShort(columnIndex)
    }

    override fun getShort(columnLabel: String?): Short {
        return current.getShort(columnLabel)
    }

    override fun getInt(columnIndex: Int): Int {
        return current.getInt(columnIndex)
    }

    override fun getInt(columnLabel: String?): Int {
        return current.getInt(columnLabel)
    }

    override fun getLong(columnIndex: Int): Long {
        return current.getLong(columnIndex)
    }

    override fun getLong(columnLabel: String?): Long {
        return current.getLong(columnLabel)
    }

    override fun getFloat(columnIndex: Int): Float {
        return current.getFloat(columnIndex)
    }

    override fun getFloat(columnLabel: String?): Float {
        return current.getFloat(columnLabel)
    }

    override fun getDouble(columnIndex: Int): Double {
        return current.getDouble(columnIndex)
    }

    override fun getDouble(columnLabel: String?): Double {
        return current.getDouble(columnLabel)
    }

    override fun getBigDecimal(columnIndex: Int, scale: Int): BigDecimal {
        return current.getBigDecimal(columnIndex)
    }

    override fun getBigDecimal(columnLabel: String?, scale: Int): BigDecimal {
        return current.getBigDecimal(columnLabel, scale)
    }

    override fun getBigDecimal(columnIndex: Int): BigDecimal {
        return current.getBigDecimal(columnIndex)
    }

    override fun getBigDecimal(columnLabel: String?): BigDecimal {
        return current.getBigDecimal(columnLabel)
    }

    override fun getBytes(columnIndex: Int): ByteArray {
        return current.getBytes(columnIndex)
    }

    override fun getBytes(columnLabel: String?): ByteArray {
        return current.getBytes(columnLabel)
    }

    override fun getDate(columnIndex: Int): Date {
        return current.getDate(columnIndex)
    }

    override fun getDate(columnLabel: String?): Date {
        return current.getDate(columnLabel)
    }

    override fun getDate(columnIndex: Int, cal: Calendar?): Date {
        return current.getDate(columnIndex, cal)
    }

    override fun getDate(columnLabel: String?, cal: Calendar?): Date {
        return current.getDate(columnLabel)
    }

    override fun getTime(columnIndex: Int): Time {
        return current.getTime(columnIndex)
    }

    override fun getTime(columnLabel: String?): Time {
        return current.getTime(columnLabel)
    }

    override fun getTime(columnIndex: Int, cal: Calendar?): Time {
        return current.getTime(columnIndex, cal)
    }

    override fun getTime(columnLabel: String?, cal: Calendar?): Time {
        return current.getTime(columnLabel)
    }

    override fun getTimestamp(columnIndex: Int): Timestamp {
        return current.getTimestamp(columnIndex)
    }

    override fun getTimestamp(columnLabel: String?): Timestamp {
        return current.getTimestamp(columnLabel)
    }

    override fun getTimestamp(columnIndex: Int, cal: Calendar?): Timestamp {
        return current.getTimestamp(columnIndex)
    }

    override fun getTimestamp(columnLabel: String?, cal: Calendar?): Timestamp {
        return current.getTimestamp(columnLabel)
    }

    override fun getAsciiStream(columnIndex: Int): InputStream {
        return current.getAsciiStream(columnIndex)
    }

    override fun getAsciiStream(columnLabel: String?): InputStream {
        return current.getAsciiStream(columnLabel)
    }

    override fun getUnicodeStream(columnIndex: Int): InputStream {
        return current.getUnicodeStream(columnIndex)
    }

    override fun getUnicodeStream(columnLabel: String?): InputStream {
        return current.getUnicodeStream(columnLabel)
    }

    override fun getBinaryStream(columnIndex: Int): InputStream {
        return current.getBinaryStream(columnIndex)
    }

    override fun getBinaryStream(columnLabel: String?): InputStream {
        return current.getBinaryStream(columnLabel)
    }

    override fun getWarnings(): SQLWarning {
        return current.warnings
    }

    override fun clearWarnings() {
        current.clearWarnings()
    }

    override fun getCursorName(): String {
        return current.cursorName
    }

    override fun getMetaData(): ResultSetMetaData {
        return current.metaData
    }

    override fun getObject(columnIndex: Int): Any {
        return current.getObject(columnIndex)
    }

    override fun getObject(columnLabel: String?): Any {
        return current.getObject(columnLabel)
    }

    override fun getObject(columnIndex: Int, map: MutableMap<String, Class<*>>?): Any {
        return current.getObject(columnIndex, map)
    }

    override fun getObject(columnLabel: String?, map: MutableMap<String, Class<*>>?): Any {
        return current.getObject(columnLabel, map)
    }

    override fun <T : Any?> getObject(columnIndex: Int, type: Class<T>?): T {
        return current.getObject(columnIndex, type)
    }

    override fun <T : Any?> getObject(columnLabel: String?, type: Class<T>?): T? {
        return current.getObject(columnLabel, type)
    }

    override fun findColumn(columnLabel: String?): Int {
        return current.findColumn(columnLabel)
    }

    override fun getCharacterStream(columnIndex: Int): Reader {
        return current.getCharacterStream(columnIndex)
    }

    override fun getCharacterStream(columnLabel: String?): Reader {
        return current.getCharacterStream(columnLabel)
    }

    override fun isBeforeFirst(): Boolean {
        return current.isBeforeFirst
    }

    override fun isAfterLast(): Boolean {
        return current.isAfterLast
    }

    override fun isFirst(): Boolean {
        return current.isFirst
    }

    override fun isLast(): Boolean {
        return current.isLast
    }

    override fun beforeFirst() {
        current.beforeFirst()
    }

    override fun afterLast() {
        current.afterLast()
    }

    override fun first(): Boolean {
        return current.first()
    }

    override fun last(): Boolean {
        return current.last()
    }

    override fun getRow(): Int {
        return current.row
    }

    override fun absolute(row: Int): Boolean {
        return current.absolute(row)
    }

    override fun relative(rows: Int): Boolean {
        return current.relative(rows)
    }

    override fun previous(): Boolean {
        return current.previous()
    }

    override fun setFetchDirection(direction: Int) {
        current.fetchDirection = direction
    }

    override fun getFetchDirection(): Int {
        return current.fetchDirection
    }

    override fun setFetchSize(rows: Int) {
        return current.setFetchSize(rows)
    }

    override fun getFetchSize(): Int {
        return current.fetchSize
    }

    override fun getType(): Int {
        return current.type
    }

    override fun getConcurrency(): Int {
        return current.concurrency
    }

    override fun rowUpdated(): Boolean {
        return current.rowUpdated()
    }

    override fun rowInserted(): Boolean {
        return current.rowInserted()
    }

    override fun rowDeleted(): Boolean {
        return current.rowDeleted()
    }

    override fun updateNull(columnIndex: Int) {
        current.updateNull(columnIndex)
    }

    override fun updateNull(columnLabel: String?) {
        current.updateNull(columnLabel)
    }

    override fun updateBoolean(columnIndex: Int, x: Boolean) {
        current.updateBoolean(columnIndex, x)
    }

    override fun updateBoolean(columnLabel: String?, x: Boolean) {
        current.updateBoolean(columnLabel, x)
    }

    override fun updateByte(columnIndex: Int, x: Byte) {
        current.updateByte(columnIndex, x)
    }

    override fun updateByte(columnLabel: String?, x: Byte) {
        current.updateByte(columnLabel, x)
    }

    override fun updateShort(columnIndex: Int, x: Short) {
        current.updateShort(columnIndex, x)
    }

    override fun updateShort(columnLabel: String?, x: Short) {
        current.updateShort(columnLabel, x)
    }

    override fun updateInt(columnIndex: Int, x: Int) {
        current.updateInt(columnIndex, x)
    }

    override fun updateInt(columnLabel: String?, x: Int) {
        current.updateInt(columnLabel, x)
    }

    override fun updateLong(columnIndex: Int, x: Long) {
        current.updateLong(columnIndex, x)
    }

    override fun updateLong(columnLabel: String?, x: Long) {
        current.updateLong(columnLabel, x)
    }

    override fun updateFloat(columnIndex: Int, x: Float) {
        current.updateFloat(columnIndex, x)
    }

    override fun updateFloat(columnLabel: String?, x: Float) {
        current.updateFloat(columnLabel, x)
    }

    override fun updateDouble(columnIndex: Int, x: Double) {
        current.updateDouble(columnIndex, x)
    }

    override fun updateDouble(columnLabel: String?, x: Double) {
        current.updateDouble(columnLabel, x)
    }

    override fun updateBigDecimal(columnIndex: Int, x: BigDecimal?) {
        current.updateBigDecimal(columnIndex, x)
    }

    override fun updateBigDecimal(columnLabel: String?, x: BigDecimal?) {
        current.updateBigDecimal(columnLabel, x)
    }

    override fun updateString(columnIndex: Int, x: String?) {
        current.updateString(columnIndex, x)
    }

    override fun updateString(columnLabel: String?, x: String?) {
        current.updateString(columnLabel, x)
    }

    override fun updateBytes(columnIndex: Int, x: ByteArray?) {
        current.updateBytes(columnIndex, x)
    }

    override fun updateBytes(columnLabel: String?, x: ByteArray?) {
        current.updateBytes(columnLabel, x)
    }

    override fun updateDate(columnIndex: Int, x: Date?) {
        current.updateDate(columnIndex, x)
    }

    override fun updateDate(columnLabel: String?, x: Date?) {
        current.updateDate(columnLabel, x)
    }

    override fun updateTime(columnIndex: Int, x: Time?) {
        current.updateTime(columnIndex, x)
    }

    override fun updateTime(columnLabel: String?, x: Time?) {
        current.updateTime(columnLabel, x)
    }

    override fun updateTimestamp(columnIndex: Int, x: Timestamp?) {
        current.updateTimestamp(columnIndex, x)
    }

    override fun updateTimestamp(columnLabel: String?, x: Timestamp?) {
        current.updateTimestamp(columnLabel, x)
    }

    override fun updateAsciiStream(columnIndex: Int, x: InputStream?, length: Int) {
        current.updateAsciiStream(columnIndex, x, length)
    }

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?, length: Int) {
        current.updateAsciiStream(columnLabel, x)
    }

    override fun updateAsciiStream(columnIndex: Int, x: InputStream?, length: Long) {
        current.updateAsciiStream(columnIndex, x, length)
    }

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?, length: Long) {
        current.updateAsciiStream(columnLabel, x)
    }

    override fun updateAsciiStream(columnIndex: Int, x: InputStream?) {
        current.updateAsciiStream(columnIndex, x)
    }

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?) {
        current.updateAsciiStream(columnLabel, x)
    }

    override fun updateBinaryStream(columnIndex: Int, x: InputStream?, length: Int) {
        current.updateBinaryStream(columnIndex, x)
    }

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?, length: Int) {
        current.updateBinaryStream(columnLabel, x, length)
    }

    override fun updateBinaryStream(columnIndex: Int, x: InputStream?, length: Long) {
        current.updateBinaryStream(columnIndex, x, length)
    }

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?, length: Long) {
        current.updateBinaryStream(columnLabel, x, length)
    }

    override fun updateBinaryStream(columnIndex: Int, x: InputStream?) {
        current.updateBinaryStream(columnIndex, x)
    }

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?) {
        current.updateBinaryStream(columnLabel, x)
    }

    override fun updateCharacterStream(columnIndex: Int, x: Reader?, length: Int) {
        current.updateCharacterStream(columnIndex, x, length)
    }

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?, length: Int) {
        current.updateCharacterStream(columnLabel, reader, length)
    }

    override fun updateCharacterStream(columnIndex: Int, x: Reader?, length: Long) {
        current.updateCharacterStream(columnIndex, x, length)
    }

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?, length: Long) {
        current.updateCharacterStream(columnLabel, reader, length)
    }

    override fun updateCharacterStream(columnIndex: Int, x: Reader?) {
        current.updateCharacterStream(columnIndex, x)
    }

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?) {
        current.updateCharacterStream(columnLabel, reader)
    }

    override fun updateObject(columnIndex: Int, x: Any?, scaleOrLength: Int) {
        current.updateObject(columnIndex, x, scaleOrLength)
    }

    override fun updateObject(columnIndex: Int, x: Any?) {
        current.updateObject(columnIndex, x)
    }

    override fun updateObject(columnLabel: String?, x: Any?, scaleOrLength: Int) {
        current.updateObject(columnLabel, x, scaleOrLength)
    }

    override fun updateObject(columnLabel: String?, x: Any?) {
        current.updateObject(columnLabel, x)
    }

    override fun insertRow() {
        current.insertRow()
    }

    override fun updateRow() {
        current.updateRow()
    }

    override fun deleteRow() {
        current.deleteRow()
    }

    override fun refreshRow() {
        current.refreshRow()
    }

    override fun cancelRowUpdates() {
        current.cancelRowUpdates()
    }

    override fun moveToInsertRow() {
        current.moveToInsertRow()
    }

    override fun moveToCurrentRow() {
        current.moveToCurrentRow()
    }

    override fun getStatement(): Statement {
        return current.statement
    }

    override fun getRef(columnIndex: Int): Ref {
        return current.getRef(columnIndex)
    }

    override fun getRef(columnLabel: String?): Ref {
        return current.getRef(columnLabel)
    }

    override fun getBlob(columnIndex: Int): Blob {
        return current.getBlob(columnIndex)
    }

    override fun getBlob(columnLabel: String?): Blob {
        return current.getBlob(columnLabel)
    }

    override fun getClob(columnIndex: Int): Clob {
        return current.getClob(columnIndex)
    }

    override fun getClob(columnLabel: String?): Clob {
        return current.getClob(columnLabel)
    }

    override fun getArray(columnIndex: Int): java.sql.Array {
        return current.getArray(columnIndex)
    }

    override fun getArray(columnLabel: String?): java.sql.Array {
        return current.getArray(columnLabel)
    }

    override fun getURL(columnIndex: Int): URL {
        return current.getURL(columnIndex)
    }

    override fun getURL(columnLabel: String?): URL {
        return current.getURL(columnLabel)
    }

    override fun updateRef(columnIndex: Int, x: Ref?) {
        current.updateRef(columnIndex, x)
    }

    override fun updateRef(columnLabel: String?, x: Ref?) {
        current.updateRef(columnLabel, x)
    }

    override fun updateBlob(columnIndex: Int, x: Blob?) {
        current.updateBlob(columnIndex, x)
    }

    override fun updateBlob(columnLabel: String?, x: Blob?) {
        current.updateBlob(columnLabel, x)
    }

    override fun updateBlob(columnIndex: Int, inputStream: InputStream?, length: Long) {
        current.updateBlob(columnIndex, inputStream, length)
    }

    override fun updateBlob(columnLabel: String?, inputStream: InputStream?, length: Long) {
        current.updateBlob(columnLabel, inputStream, length)
    }

    override fun updateBlob(columnIndex: Int, inputStream: InputStream?) {
        current.updateBlob(columnIndex, inputStream)
    }

    override fun updateBlob(columnLabel: String?, inputStream: InputStream?) {
        current.updateBlob(columnLabel, inputStream)
    }

    override fun updateClob(columnIndex: Int, x: Clob?) {
        current.updateClob(columnIndex, x)
    }

    override fun updateClob(columnLabel: String?, x: Clob?) {
        current.updateClob(columnLabel, x)
    }

    override fun updateClob(columnIndex: Int, reader: Reader?, length: Long) {
        current.updateClob(columnIndex, reader, length)
    }

    override fun updateClob(columnLabel: String?, reader: Reader?, length: Long) {
        current.updateClob(columnLabel, reader, length)
    }

    override fun updateClob(columnIndex: Int, reader: Reader?) {
        current.updateClob(columnIndex, reader)
    }

    override fun updateClob(columnLabel: String?, reader: Reader?) {
        current.updateClob(columnLabel, reader)
    }

    override fun updateArray(columnIndex: Int, x: java.sql.Array?) {
        current.updateArray(columnIndex, x)
    }

    override fun updateArray(columnLabel: String?, x: java.sql.Array?) {
        current.updateArray(columnLabel, x)
    }

    override fun getRowId(columnIndex: Int): RowId {
        return current.getRowId(columnIndex)
    }

    override fun getRowId(columnLabel: String?): RowId {
        return current.getRowId(columnLabel)
    }

    override fun updateRowId(columnIndex: Int, x: RowId?) {
        current.updateRowId(columnIndex, x)
    }

    override fun updateRowId(columnLabel: String?, x: RowId?) {
        current.updateRowId(columnLabel, x)
    }

    override fun getHoldability(): Int {
        return resultSet.sumOf { it.holdability }
    }

    override fun isClosed(): Boolean {
        return resultSet.all { isClosed }
    }

    override fun updateNString(columnIndex: Int, nString: String?) {
        current.updateNString(columnIndex, nString)
    }

    override fun updateNString(columnLabel: String?, nString: String?) {
        current.updateNString(columnLabel, nString)
    }

    override fun updateNClob(columnIndex: Int, nClob: NClob?) {
        current.updateNClob(columnIndex, nClob)
    }

    override fun updateNClob(columnLabel: String?, nClob: NClob?) {
        current.updateNClob(columnLabel, nClob)
    }

    override fun updateNClob(columnIndex: Int, reader: Reader?, length: Long) {
        current.updateNClob(columnIndex, reader, length)
    }

    override fun updateNClob(columnLabel: String?, reader: Reader?, length: Long) {
        current.updateNClob(columnLabel, reader, length)
    }

    override fun updateNClob(columnIndex: Int, reader: Reader?) {
        current.updateNClob(columnIndex, reader!!)
    }

    override fun updateNClob(columnLabel: String?, reader: Reader?) {
        current.updateNClob(columnLabel, reader)
    }

    override fun getNClob(columnIndex: Int): NClob {
        return current.getNClob(columnIndex)
    }

    override fun getNClob(columnLabel: String?): NClob {
        return current.getNClob(columnLabel)
    }

    override fun getSQLXML(columnIndex: Int): SQLXML {
        return current.getSQLXML(columnIndex)
    }

    override fun getSQLXML(columnLabel: String?): SQLXML {
        return current.getSQLXML(columnLabel)
    }

    override fun updateSQLXML(columnIndex: Int, xmlObject: SQLXML?) {
        current.updateSQLXML(columnIndex, xmlObject)
    }

    override fun updateSQLXML(columnLabel: String?, xmlObject: SQLXML?) {
        current.updateSQLXML(columnLabel, xmlObject)
    }

    override fun getNString(columnIndex: Int): String {
        return current.getNString(columnIndex)
    }

    override fun getNString(columnLabel: String?): String {
        return current.getNString(columnLabel)
    }

    override fun getNCharacterStream(columnIndex: Int): Reader {
        return current.getNCharacterStream(columnIndex)
    }

    override fun getNCharacterStream(columnLabel: String?): Reader {
        return current.getNCharacterStream(columnLabel)
    }

    override fun updateNCharacterStream(columnIndex: Int, x: Reader?, length: Long) {
        current.updateNCharacterStream(columnIndex, x, length)
    }

    override fun updateNCharacterStream(columnLabel: String?, reader: Reader?, length: Long) {
        current.updateNCharacterStream(columnLabel, reader, length)
    }

    override fun updateNCharacterStream(columnIndex: Int, x: Reader?) {
        current.updateNCharacterStream(columnIndex, x)
    }

    override fun updateNCharacterStream(columnLabel: String?, reader: Reader?) {
        return current.updateNCharacterStream(columnLabel, reader)
    }

}
