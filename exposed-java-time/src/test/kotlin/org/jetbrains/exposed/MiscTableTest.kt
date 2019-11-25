package org.jetbrains.exposed

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.date
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.MiscTable
import org.jetbrains.exposed.sql.tests.shared.checkInsert
import org.jetbrains.exposed.sql.tests.shared.checkRow
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

object Misc : MiscTable() {
    val d = date("d")
    val dn = date("dn").nullable()

    val t = datetime("t")
    val tn = datetime("tn").nullable()
}

class MiscTableTest : DatabaseTestsBase() {
    @Test
    fun testInsert01() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()

        withTables(tbl) {
            tbl.insert {
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                    null, "test", null, BigDecimal("239.42"), null, null, null)
            tbl.checkRowDates(row, date, null, time, null)
            assertEquals('(', row[tbl.char])
        }
    }

    @Test
    fun testInsert02() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()

        withTables(tbl) {
            tbl.insert {
                it[sm] = -10
                it[smn] = null
                it[n] = 42
                it[nn] = null
                it[d] = date
                it[dn] = null
                it[t] = time
                it[tn] = null
                it[e] = MiscTable.E.ONE
                it[en] = null
                it[es] = MiscTable.E.ONE
                it[esn] = null
                it[s] = "test"
                it[sn] = null
                it[dc] = BigDecimal("239.42")
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                    null, "test", null, BigDecimal("239.42"), null, null, null)
            tbl.checkRowDates(row, date, null, time, null)
        }
    }

    @Test
    fun testInsert03() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()

        withTables(tbl) {
            tbl.insert {
                it[sm] = -10
                it[smn] = -10
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = MiscTable.E.ONE
                it[en] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[esn] = MiscTable.E.ONE
                it[s] = "test"
                it[sn] = "test"
                it[dc] = BigDecimal("239.42")
                it[dcn] = BigDecimal("239.42")
                it[fcn] = 239.42f
                it[dblcn] = 567.89
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, -10, -10, 42, 42, MiscTable.E.ONE, MiscTable.E.ONE, MiscTable.E.ONE, MiscTable.E.ONE,
                    "test", "test", BigDecimal("239.42"), BigDecimal("239.42"), 239.42f, 567.89)
            tbl.checkRowDates(row, date, date, time, time)
        }
    }

    @Test
    fun testInsert04() {
        val stringThatNeedsEscaping = "A'braham Barakhyahu"
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        withTables(tbl) {
            tbl.insert {
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[s] = stringThatNeedsEscaping
                it[dc] = BigDecimal("239.42")
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, stringThatNeedsEscaping, null,
                    BigDecimal("239.42"), null, null, null)
            tbl.checkRowDates(row, date, null, time, null)
        }
    }

    @Test
    fun testInsertGet01() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()

        withTables(tbl) {
            val row = tbl.insert {
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            tbl.checkInsert(row, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                    null, "test", null, BigDecimal("239.42"), null, null, null)
            tbl.checkRowDates(row.resultedValues!!.single(), date, null, time, null)
            assertEquals('(', row[tbl.char])
        }
    }

    @Test
    fun testSelect01() {
        val tbl = Misc
        withTables(tbl) {
            val date = today
            val time = LocalDateTime.now()
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[s] = sTest
                it[dc] = dec
            }

            tbl.checkRowFull(tbl.select { tbl.n.eq(42) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.nn.isNull() }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.nn.eq(null as Int?) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRowFull(tbl.select { tbl.d.eq(date) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.dn.isNull() }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.dn.eq(null as LocalDate?) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRowFull(tbl.select { tbl.t.eq(time) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.tn.isNull() }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.tn.eq(null as LocalDateTime?) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRowFull(tbl.select { tbl.e.eq(MiscTable.E.ONE) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.en.isNull() }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.en.eq(null as MiscTable.E?) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRowFull(tbl.select { tbl.s.eq(sTest) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.sn.isNull() }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRowFull(tbl.select { tbl.sn.eq(null as String?) }.single(), -10, null, 42, null, date, null, time, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null, sTest, null, dec, null, null, null)
        }
    }

    @Test
    fun testSelect02() {
        val tbl = Misc
        withTables(tbl) {
            val date = today
            val time = LocalDateTime.now()
            val sTest = "test"
            val eOne = MiscTable.E.ONE
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[sm] = -10
                it[smn] = -10
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[es] = eOne
                it[esn] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
                it[fcn] = 239.42f
                it[dblcn] = 567.89
            }

            tbl.checkRowFull(tbl.select { tbl.nn.eq(42) }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRowFull(tbl.select { tbl.nn.neq<Int?>(null) }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRowFull(tbl.select { tbl.dn.eq(date) }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRowFull(tbl.select { tbl.dn.isNotNull() }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRowFull(tbl.select { tbl.t.eq(time) }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRowFull(tbl.select { tbl.tn.isNotNull() }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRowFull(tbl.select { tbl.en.eq(eOne) }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRowFull(tbl.select { tbl.en.isNotNull() }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRowFull(tbl.select { tbl.sn.eq(sTest) }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRowFull(tbl.select { tbl.sn.isNotNull() }.single(), -10, -10, 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
        }
    }

    @Test
    fun testUpdate02() {
        val tbl = Misc
        withTables(tbl) {
            val date = today
            val time = LocalDateTime.now()
            val eOne = MiscTable.E.ONE
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[sm] = -10
                it[smn] = -10
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[es] = eOne
                it[esn] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
                it[fcn] = 239.42f
            }

            tbl.update({ tbl.n.eq(42) }) {
                it[smn] = null
                it[nn] = null
                it[dn] = null
                it[tn] = null
                it[en] = null
                it[esn] = null
                it[sn] = null
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRowFull(row, -10, null, 42, null, date, null, time, null, eOne, null, eOne, null, sTest, null, dec, null, null, null)
        }
    }

    @Test
    fun testUpdate03() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val eOne = MiscTable.E.ONE
        val dec = BigDecimal("239.42")
        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB), tables = *arrayOf(tbl)) {
            tbl.insert {
                it[sm] = -10
                it[n] = 101
                it[s] = "123456789"
                it[sn] = "123456789"
                it[d] = date
                it[t] = time
                it[e] = eOne
                it[es] = eOne
                it[dc] = dec
            }

            tbl.update({ tbl.n.eq(101) }) {
                it.update(s, tbl.s.substring(2, 255))
                it.update(sn, tbl.s.substring(3, 255))
            }

            val row = tbl.select { tbl.n eq 101 }.single()
            tbl.checkRowFull(row, -10, null, 101, null, date, null, time, null, eOne, null, eOne, null, "23456789", "3456789", dec, null, null, null)
        }
    }
}

fun Misc.checkRowFull(row: ResultRow,
                      sm: Short, smn: Short?,
                      n: Int, nn: Int?,
                      d: LocalDate, dn: LocalDate?, t: LocalDateTime, tn: LocalDateTime?,
                      e: MiscTable.E, en: MiscTable.E?,
                      es: MiscTable.E, esn: MiscTable.E?, s: String, sn: String?,
                      dc: BigDecimal, dcn: BigDecimal?, fcn: Float?, dblcn: Double?) {
    checkRow(row,sm, smn, n, nn, e, en, es, esn, s, sn, dc, dcn, fcn, dblcn)
    checkRowDates(row, d, dn, t, tn)
}

fun Misc.checkRowDates(row: ResultRow, d: LocalDate, dn: LocalDate?, t: LocalDateTime, tn: LocalDateTime?) {
    assertEqualDateTime(d, row[this.d])
    assertEqualDateTime(dn, row[this.dn])
    assertEqualDateTime(t, row[this.t])
    assertEqualDateTime(tn, row[this.tn])
}

