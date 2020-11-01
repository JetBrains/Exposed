package org.jetbrains.exposed

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.date
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.`java-time`.datetimetz
import org.jetbrains.exposed.sql.`java-time`.timestamp
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.MiscTable
import org.jetbrains.exposed.sql.tests.shared.checkInsert
import org.jetbrains.exposed.sql.tests.shared.checkRow
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.test.assertEquals

object Misc : MiscTable() {
    val d = date("d")
    val dn = date("dn").nullable()

    val t = datetime("t")
    val tn = datetime("tn").nullable()

    val ts = timestamp("ts")
    val tsn = timestamp("tsn").nullable()

    val ttz = datetimetz("ttz")
    val ttzn = datetimetz("ttzn").nullable()
}

class MiscTableTest : DatabaseTestsBase() {
    @Test
    fun testInsert01() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val timestamp = Instant.now()
        val datetimeWithTimezone = todayWithZone

        withTables(tbl) {
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[ts] = timestamp
                it[ttz] = datetimeWithTimezone
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[c] = "test"
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(
                row,  13, null,-10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                null, "test", null, "test", null, BigDecimal("239.42"), null, null, null
            )
            tbl.checkRowDates(row, date, null, time, null, timestamp, null, datetimeWithTimezone, null)
            assertEquals('(', row[tbl.char])
        }
    }

    @Test
    fun testInsert02() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val timestamp = Instant.now()
        val datetimeWithTimezone = todayWithZone

        withTables(tbl) {
            tbl.insert {
                it[by] = 13
                it[byn] = null
                it[sm] = -10
                it[smn] = null
                it[n] = 42
                it[nn] = null
                it[d] = date
                it[dn] = null
                it[t] = time
                it[ts] = timestamp
                it[tn] = null
                it[ttz] = datetimeWithTimezone
                it[ttzn] = null
                it[e] = MiscTable.E.ONE
                it[en] = null
                it[es] = MiscTable.E.ONE
                it[esn] = null
                it[c] = "test"
                it[cn] = null
                it[s] = "test"
                it[sn] = null
                it[dc] = BigDecimal("239.42")
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(
                row, 13, null, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                null, "test", null, "test", null, BigDecimal("239.42"), null, null, null
            )
            tbl.checkRowDates(row, date, null, time, null, timestamp, null, datetimeWithTimezone, null)
        }
    }

    @Test
    fun testInsert03() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val timestamp = Instant.now()
        val datetimeWithTimezone = todayWithZone

        withTables(tbl) {
            tbl.insert {
                it[by] = 13
                it[byn] = 13
                it[sm] = -10
                it[smn] = -10
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[ts] = timestamp
                it[tn] = time
                it[ttz] = datetimeWithTimezone
                it[ttzn] = datetimeWithTimezone
                it[e] = MiscTable.E.ONE
                it[en] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[esn] = MiscTable.E.ONE
                it[c] = "test"
                it[cn] = "test"
                it[s] = "test"
                it[sn] = "test"
                it[dc] = BigDecimal("239.42")
                it[dcn] = BigDecimal("239.42")
                it[fcn] = 239.42f
                it[dblcn] = 567.89
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(
                row, 13, 13, -10, -10, 42, 42, MiscTable.E.ONE, MiscTable.E.ONE, MiscTable.E.ONE, MiscTable.E.ONE,
                "test", "test", "test", "test", BigDecimal("239.42"), BigDecimal("239.42"), 239.42f, 567.89
            )
            tbl.checkRowDates(row, date, date, time, time, timestamp, null, datetimeWithTimezone, datetimeWithTimezone)
        }
    }

    @Test
    fun testInsert04() {
        val shortStringThatNeedsEscaping = "A'br"
        val stringThatNeedsEscaping = "A'braham Barakhyahu"
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val timestamp = Instant.now()
        val datetimeWithTimezone = todayWithZone

        withTables(tbl) {
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[ts] = timestamp
                it[ttz] = datetimeWithTimezone
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[c] = shortStringThatNeedsEscaping
                it[s] = stringThatNeedsEscaping
                it[dc] = BigDecimal("239.42")
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(
                row, 13, null, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE, null,
                shortStringThatNeedsEscaping, null, stringThatNeedsEscaping, null,
                BigDecimal("239.42"), null, null, null
            )
            tbl.checkRowDates(row, date, null, time, null, timestamp, null, datetimeWithTimezone, null)
        }
    }

    @Test
    fun testInsertGet01() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val datetimeWithTimeZone = todayWithZone
        val timestamp = Instant.now()
        val datetimeWithTimezone = todayWithZone

        withTables(tbl) {
            val row = tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[ts] = timestamp
                it[tsn] = timestamp
                it[ttz] = datetimeWithTimeZone
                it[ttzn] = datetimeWithTimeZone
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[c] = "test"
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            tbl.checkInsert(
                row, 13, null, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                null, "test", null, BigDecimal("239.42"), null, null, null
            )
            tbl.checkRowDates(row = row.resultedValues!!.single(), d = date, dn = null, t = time, tn = null, ts = timestamp, tsn = timestamp, ttz = datetimeWithTimezone, ttzn = datetimeWithTimezone)
            assertEquals('(', row[tbl.char])
        }
    }

    @Test
    fun testSelect01() {
        val tbl = Misc
        withTables(tbl) {
            val date = today
            val time = LocalDateTime.now()
            val timestamp = Instant.now()
            val datetimeWithTimezone = todayWithZone
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[ts] = timestamp
                it[ttz] = datetimeWithTimezone
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[c] = sTest
                it[s] = sTest
                it[dc] = dec
            }

            tbl.checkRowFull(
                tbl.select { tbl.n.eq(42) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = null,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.nn.isNull() }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = null,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.nn.eq(null as Int?) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = null,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )

            tbl.checkRowFull(
                tbl.select { tbl.d.eq(date) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = null,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.dn.isNull() }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = null,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )

            tbl.checkRowFull(
                tbl.select { tbl.t.eq(time) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = null,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.tn.isNull() }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )

            tbl.checkRowFull(
                tbl.select { tbl.e.eq(MiscTable.E.ONE) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.en.isNull() }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.en.eq(null as MiscTable.E?) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )

            tbl.checkRowFull(
                tbl.select { tbl.s.eq(sTest) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.sn.isNull() }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
            tbl.checkRowFull(
                tbl.select { tbl.sn.eq(null as String?) }.single(),
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 42,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                ttz = datetimeWithTimezone,
                ttzn = null,
                ts = timestamp,
                e = MiscTable.E.ONE,
                en = null,
                es = MiscTable.E.ONE,
                esn = null,
                c = sTest,
                cn = null,
                s = sTest,
                sn = null,
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
        }
    }

    @Test
    fun testSelect02() {
        val tbl = Misc
        withTables(tbl) {
            val date = today
            val time = LocalDateTime.now()
            val timestamp = Instant.now()
            val datetimeWithTimezone = todayWithZone
            val sTest = "test"
            val eOne = MiscTable.E.ONE
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[by] = 13
                it[byn] = 13
                it[sm] = -10
                it[smn] = -10
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[ts] = timestamp
                it[tn] = time
                it[ttz] = datetimeWithTimezone
                it[ttzn] = datetimeWithTimezone
                it[e] = eOne
                it[en] = eOne
                it[es] = eOne
                it[esn] = eOne
                it[c] = sTest
                it[cn] = sTest
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
                it[fcn] = 239.42f
                it[dblcn] = 567.89
            }

            tbl.checkRowFull(
                tbl.select { tbl.nn.eq(42) }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )
            tbl.checkRowFull(
                tbl.select { tbl.nn.neq<Int?>(null) }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )

            tbl.checkRowFull(
                tbl.select { tbl.dn.eq(date) }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )
            tbl.checkRowFull(
                tbl.select { tbl.dn.isNotNull() }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )

            tbl.checkRowFull(
                tbl.select { tbl.t.eq(time) }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )
            tbl.checkRowFull(
                tbl.select { tbl.tn.isNotNull() }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )

            tbl.checkRowFull(
                tbl.select { tbl.en.eq(eOne) }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )
            tbl.checkRowFull(
                tbl.select { tbl.en.isNotNull() }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )

            tbl.checkRowFull(
                tbl.select { tbl.sn.eq(sTest) }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )
            tbl.checkRowFull(
                tbl.select { tbl.sn.isNotNull() }.single(),
                by = 13,
                byn = 13,
                sm = -10,
                smn = -10,
                n = 42,
                nn = 42,
                d = date,
                dn = date,
                t = time,
                tn = time,
                ts = timestamp,
                ttz = datetimeWithTimezone,
                ttzn = datetimeWithTimezone,
                e = eOne,
                en = eOne,
                es = eOne,
                esn = eOne,
                c = sTest,
                cn = sTest,
                s = sTest,
                sn = sTest,
                dc = dec,
                dcn = dec,
                fcn = 239.42f,
                dblcn = 567.89
            )
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
            val timestamp = Instant.now()
            val datetimeWithTimezone = todayWithZone
            tbl.insert {
                it[by] = 13
                it[byn] = 13
                it[sm] = -10
                it[smn] = -10
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[ts] = timestamp
                it[ttz] = datetimeWithTimezone
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[es] = eOne
                it[esn] = eOne
                it[c] = sTest
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
                it[fcn] = 239.42f
            }

            tbl.update({ tbl.n.eq(42) }) {
                it[byn] = null
                it[smn] = null
                it[nn] = null
                it[dn] = null
                it[tn] = null
                it[ttzn] = null
                it[en] = null
                it[esn] = null
                it[cn] = null
                it[sn] = null
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRowFull(
                    row = row, by = 13, byn = null, sm = -10, smn = null, n = 42, nn = null, d = date, dn = null, t = time, tn = null, ts = timestamp,
                    e = eOne, en = null, es = eOne, esn = null,
                    c = sTest, cn = null, s = sTest, sn = null,
                    dc = dec, dcn = null, fcn = null, dblcn = null,
                    ttz = datetimeWithTimezone, ttzn = null
            )
        }
    }

    @Test
    fun testUpdate03() {
        val tbl = Misc
        val date = today
        val time = LocalDateTime.now()
        val timestamp = Instant.now()
        val datetimeWithTimezone = todayWithZone
        val eOne = MiscTable.E.ONE
        val dec = BigDecimal("239.42")
        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB), tables = *arrayOf(tbl)) {
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 101
                it[c] = "1234"
                it[cn] = "1234"
                it[s] = "123456789"
                it[sn] = "123456789"
                it[d] = date
                it[t] = time
                it[ts] = timestamp
                it[ttz] = datetimeWithTimezone
                it[e] = eOne
                it[es] = eOne
                it[dc] = dec
            }

            tbl.update({ tbl.n.eq(101) }) {
                it.update(s, tbl.s.substring(2, 255))
                it.update(sn) { tbl.s.substring(3, 255) }
            }

            val row = tbl.select { tbl.n eq 101 }.single()
            tbl.checkRowFull(
                    row = row, by = 13, byn = null, sm = -10, smn = null, n = 101, nn = null, d = date, dn = null, t = time,
                    tn = null, ts = timestamp, e = eOne, en = null, es = eOne, esn = null,
                    c = "1234", cn = "1234", s = "23456789", sn = "3456789",
                    dc = dec, dcn = null, fcn = null, dblcn = null,
                    ttz = datetimeWithTimezone, ttzn = null
            )
        }
    }
}

fun Misc.checkRowFull(
    row: ResultRow,
    by: Byte, byn: Byte?,
    sm: Short, smn: Short?,
    n: Int, nn: Int?,
    d: LocalDate, dn: LocalDate?, t: LocalDateTime, tn: LocalDateTime?, ts: Instant,
    e: MiscTable.E, en: MiscTable.E?,
    es: MiscTable.E, esn: MiscTable.E?,
    c: String, cn: String?, s: String, sn: String?,
    dc: BigDecimal, dcn: BigDecimal?, fcn: Float?, dblcn: Double?,
    ttz: ZonedDateTime, ttzn: ZonedDateTime?
) {
    checkRow(row, by, byn, sm, smn, n, nn, e, en, es, esn, c, cn, s, sn, dc, dcn, fcn, dblcn)
    checkRowDates(row, d, dn, t, tn, ts, null, ttz, ttzn)
}

fun Misc.checkRowDates(
        row: ResultRow,
        d: LocalDate,
        dn: LocalDate?,
        t: LocalDateTime,
        tn: LocalDateTime?,
        ts: Instant,
        tsn: Instant? = null,
        ttz: ZonedDateTime,
        ttzn: ZonedDateTime? = null
) {
    assertEqualDateTime(d, row[this.d])
    assertEqualDateTime(dn, row[this.dn])
    assertEqualDateTime(t, row[this.t])
    assertEqualDateTime(tn, row[this.tn])
    assertEqualDateTime(ts, row[this.ts])
    assertEqualDateTime(tsn, row[this.tsn])
    assertEqualDateTime(ttz, row[this.ttz])
    assertEqualDateTime(ttzn, row[this.ttzn])
}

