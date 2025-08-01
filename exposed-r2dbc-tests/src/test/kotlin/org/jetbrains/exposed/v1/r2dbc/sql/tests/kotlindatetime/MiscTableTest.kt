@file:OptIn(ExperimentalTime::class)
@file:Suppress("MaximumLineLength", "LongMethod")

package org.jetbrains.exposed.v1.r2dbc.sql.tests.kotlindatetime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toDeprecatedInstant
import org.jetbrains.exposed.v1.core.Cast
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.substring
import org.jetbrains.exposed.v1.datetime.*
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.MiscTable
import org.jetbrains.exposed.v1.r2dbc.tests.shared.checkInsert
import org.jetbrains.exposed.v1.r2dbc.tests.shared.checkRow
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Suppress("LargeClass")
class MiscTableTest : R2dbcDatabaseTestsBase() {
    @Test
    fun testInsert01() {
        val tbl = Misc
        val date = today
        val dateTime = now()
        val time = dateTime.time
        val timestamp = Clock.System.now()
        val duration = 1.minutes

        withTables(tbl) {
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[dt] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[dr] = duration
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[c] = "test"
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(
                row, 13, null, -10, null, 42, null, MiscTable.E.ONE, null, MiscTable.E.ONE,
                null, "test", null, "test", null, BigDecimal("239.42"), null, null, null
            )
            tbl.checkRowDates(row, date, null, time, null, dateTime, null, timestamp, null, duration, null)
            assertEquals('(', row[tbl.char])
        }
    }

    @Test
    fun testInsert02() {
        val tbl = Misc
        val date = today
        val dateTime = now()
        val time = dateTime.time
        val timestamp = Clock.System.now()
        val duration = 1.minutes

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
                it[tn] = null
                it[dt] = dateTime
                it[dtn] = null
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[tsn] = null
                it[xtsn] = null
                it[dr] = duration
                it[drn] = null
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
            tbl.checkRowDates(row, date, null, time, null, dateTime, null, timestamp, null, duration, null)
        }
    }

    @Test
    fun testInsert03() {
        val tbl = Misc
        val date = today
        val dateTime = now()
        val time = dateTime.time
        val timestamp = Clock.System.now()
        val duration = 1.minutes

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
                it[tn] = time
                it[dt] = dateTime
                it[dtn] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[tsn] = timestamp
                it[xtsn] = timestamp.toDeprecatedInstant()
                it[dr] = duration
                it[drn] = duration
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
            tbl.checkRowDates(row, date, date, time, time, dateTime, dateTime, timestamp, timestamp, duration, duration)
        }
    }

    @Test
    fun testInsert04() {
        val shortStringThatNeedsEscaping = "A'br"
        val stringThatNeedsEscaping = "A'braham Barakhyahu"
        val tbl = Misc
        val date = today
        val dateTime = now()
        val time = dateTime.time
        val timestamp = Clock.System.now()
        val duration = 1.minutes

        withTables(tbl) {
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[dt] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[dr] = duration
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
            tbl.checkRowDates(row, date, null, time, null, dateTime, null, timestamp, null, duration, null)
        }
    }

    @Test
    fun testInsertGet01() {
        val tbl = Misc
        val date = today
        val dateTime = now()
        val time = dateTime.time
        val timestamp = Clock.System.now()
        val duration = 1.minutes

        withTables(tbl) {
            val row = tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[dt] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[dr] = duration
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
            tbl.checkRowDates(row.resultedValues!!.single(), date, null, time, null, dateTime, null, timestamp, null, duration, null)
            assertEquals('(', row[tbl.char])
        }
    }

    // these DB take the datetime nanosecond value and round up to default precision
    // which causes flaky comparison failures if not cast to TIMESTAMP first
    private val requiresExplicitDTCast = listOf(TestDB.ORACLE, TestDB.H2_V2_ORACLE, TestDB.H2_V2_PSQL, TestDB.H2_V2_SQLSERVER)

    @Test
    fun testSelect01() {
        val tbl = Misc
        withTables(tbl) { testDb ->
            val date = today
            val dateTime = now()
            val time = dateTime.time
            val timestamp = Clock.System.now()
            val duration = 1.minutes
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[by] = 13
                it[sm] = -10
                it[n] = 42
                it[d] = date
                it[t] = time
                it[dt] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[dr] = duration
                it[e] = MiscTable.E.ONE
                it[es] = MiscTable.E.ONE
                it[c] = sTest
                it[s] = sTest
                it[dc] = dec
            }

            tbl.checkRowFull(
                tbl.selectAll().where { tbl.n.eq(42) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.nn.isNull() }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.nn.eq(null as Int?) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.d.eq(date) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.dn.isNull() }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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

            val dtValue = when (testDb) {
                in requiresExplicitDTCast -> Cast(dateTimeParam(dateTime), KotlinLocalDateTimeColumnType())
                else -> dateTimeParam(dateTime)
            }
            tbl.checkRowFull(
                tbl.selectAll().where { tbl.dt.eq(dtValue) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.dtn.isNull() }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.e.eq(MiscTable.E.ONE) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.en.isNull() }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.en.eq(null as MiscTable.E?) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.s.eq(sTest) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.sn.isNull() }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
                tbl.selectAll().where { tbl.sn.eq(null as String?) }.single(),
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
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
        withTables(tbl) { testDb ->
            val date = today
            val dateTime = now()
            val time = dateTime.time
            val timestamp = Clock.System.now()
            val duration = 1.minutes
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
                it[tn] = time
                it[dt] = dateTime
                it[dtn] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[tsn] = timestamp
                it[xtsn] = timestamp.toDeprecatedInstant()
                it[dr] = duration
                it[drn] = duration
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
                tbl.selectAll().where { tbl.nn.eq(42) }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.nn.neq(null) }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.dn.eq(date) }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.dn.isNotNull() }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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

            val dtValue = when (testDb) {
                in requiresExplicitDTCast -> Cast(dateTimeParam(dateTime), KotlinLocalDateTimeColumnType())
                else -> dateTimeParam(dateTime)
            }
            tbl.checkRowFull(
                tbl.selectAll().where { tbl.dt.eq(dtValue) }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.dtn.isNotNull() }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.en.eq(eOne) }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.en.isNotNull() }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.sn.eq(sTest) }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
                tbl.selectAll().where { tbl.sn.isNotNull() }.single(),
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
                dt = dateTime,
                dtn = dateTime,
                ts = timestamp,
                tsn = timestamp,
                dr = duration,
                drn = duration,
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
            val dateTime = now()
            val time = dateTime.time
            val eOne = MiscTable.E.ONE
            val sTest = "test"
            val dec = BigDecimal("239.42")
            val timestamp = Clock.System.now()
            val duration = 1.minutes
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
                it[tn] = time
                it[dt] = dateTime
                it[dtn] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[tsn] = timestamp
                it[xtsn] = timestamp.toDeprecatedInstant()
                it[dr] = duration
                it[drn] = duration
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
                it[dtn] = null
                it[tsn] = null
                it[xtsn] = null
                it[drn] = null
                it[en] = null
                it[esn] = null
                it[cn] = null
                it[sn] = null
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRowFull(
                row,
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
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
                e = eOne,
                en = null,
                es = eOne,
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
    fun testUpdate03() {
        val tbl = Misc
        val date = today
        val dateTime = now()
        val time = dateTime.time
        val timestamp = Clock.System.now()
        val duration = 1.minutes
        val eOne = MiscTable.E.ONE
        val dec = BigDecimal("239.42")
        withTables(excludeSettings = TestDB.ALL_MYSQL + TestDB.ALL_MARIADB, tables = arrayOf(tbl)) {
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
                it[dt] = dateTime
                it[ts] = timestamp
                it[xts] = timestamp.toDeprecatedInstant()
                it[dr] = duration
                it[e] = eOne
                it[es] = eOne
                it[dc] = dec
            }

            tbl.update({ tbl.n.eq(101) }) {
                it.update(s, tbl.s.substring(2, 255))
                it.update(sn) { tbl.s.substring(3, 255) }
            }

            val row = tbl.selectAll().where { tbl.n eq 101 }.single()

            tbl.checkRowFull(
                row,
                by = 13,
                byn = null,
                sm = -10,
                smn = null,
                n = 101,
                nn = null,
                d = date,
                dn = null,
                t = time,
                tn = null,
                dt = dateTime,
                dtn = null,
                ts = timestamp,
                tsn = null,
                dr = duration,
                drn = null,
                e = eOne,
                en = null,
                es = eOne,
                esn = null,
                c = "1234",
                cn = "1234",
                s = "23456789",
                sn = "3456789",
                dc = dec,
                dcn = null,
                fcn = null,
                dblcn = null
            )
        }
    }

    private object ZeroDateTimeTable : Table("zerodatetimetable") {
        val id = integer("id")

        val dt1 = datetime("dt1").nullable() // We need nullable() to convert '0000-00-00 00:00:00' to null
        val dt2 = datetime("dt2").nullable()
        val ts1 = datetime("ts1").nullable() // We need nullable() to convert '0000-00-00 00:00:00' to null
        val ts2 = datetime("ts2").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    private val zeroDateTimeTableDdl = """
        CREATE TABLE `zerodatetimetable` (
        `id` INT NOT NULL AUTO_INCREMENT,
        `dt1` datetime NOT NULL,
        `dt2` datetime NULL,
        `ts1` timestamp NOT NULL,
        `ts2` timestamp NULL,
        PRIMARY KEY (`id`)
    ) ENGINE=InnoDB
    """.trimIndent()

    @Test
    fun testZeroDateTimeIsNull() {
        withDb(TestDB.ALL_MYSQL_MARIADB) {
            exec(zeroDateTimeTableDdl)
            try {
                // Need ignore to bypass strict mode
                exec("INSERT IGNORE INTO `zerodatetimetable` (dt1,dt2,ts1,ts2) VALUES ('0000-00-00 00:00:00', '0000-00-00 00:00:00', '0000-00-00 00:00:00', '0000-00-00 00:00:00');")
                val row = ZeroDateTimeTable.selectAll().first()

                listOf(ZeroDateTimeTable.dt1, ZeroDateTimeTable.dt2, ZeroDateTimeTable.ts1, ZeroDateTimeTable.ts2).forEach { c ->
                    val actual = row[c]
                    assertNull(actual, "$c expected null but was $actual")
                }
                commit() // Need commit to persist data before drop tables
            } finally {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(ZeroDateTimeTable)
                commit()
            }
        }
    }
}

object Misc : MiscTable() {
    val d = date("d")
    val dn = date("dn").nullable()

    val t = time("t")
    val tn = time("tn").nullable()

    val dt = datetime("dt")
    val dtn = datetime("dtn").nullable()

    val ts = timestamp("ts")
    val tsn = timestamp("tsn").nullable()

    val xts = xTimestamp("xts")
    val xtsn = xTimestamp("xtsn").nullable()

    val dr = duration("dr")
    val drn = duration("drn").nullable()
}

@Suppress("LongParameterList")
fun Misc.checkRowFull(
    row: ResultRow,
    by: Byte,
    byn: Byte?,
    sm: Short,
    smn: Short?,
    n: Int,
    nn: Int?,
    d: LocalDate,
    dn: LocalDate?,
    t: LocalTime,
    tn: LocalTime?,
    dt: LocalDateTime,
    dtn: LocalDateTime?,
    ts: Instant,
    tsn: Instant?,
    dr: Duration,
    drn: Duration?,
    e: MiscTable.E,
    en: MiscTable.E?,
    es: MiscTable.E,
    esn: MiscTable.E?,
    c: String,
    cn: String?,
    s: String,
    sn: String?,
    dc: BigDecimal,
    dcn: BigDecimal?,
    fcn: Float?,
    dblcn: Double?
) {
    checkRow(row, by, byn, sm, smn, n, nn, e, en, es, esn, c, cn, s, sn, dc, dcn, fcn, dblcn)
    checkRowDates(row, d, dn, t, tn, dt, dtn, ts, tsn, dr, drn)
}

@Suppress("LongParameterList")
fun Misc.checkRowDates(
    row: ResultRow,
    d: LocalDate,
    dn: LocalDate?,
    t: LocalTime,
    tn: LocalTime?,
    dt: LocalDateTime,
    dtn: LocalDateTime?,
    ts: Instant,
    tsn: Instant? = null,
    dr: Duration,
    drn: Duration? = null
) {
    assertEquals(d, row[this.d])
    assertEquals(dn, row[this.dn])
    assertLocalTime(t, row[this.t])
    assertLocalTime(tn, row[this.tn])
    assertEqualDateTime(dt, row[this.dt])
    assertEqualDateTime(dtn, row[this.dtn])
    assertEqualDateTime(ts, row[this.ts])
    assertEqualDateTime(tsn, row[this.tsn])
    assertEqualDateTime(ts.toDeprecatedInstant(), row[this.xts])
    assertEqualDateTime(tsn?.toDeprecatedInstant(), row[this.xtsn])
    assertEquals(dr, row[this.dr])
    assertEquals(drn, row[this.drn])
}

private fun assertLocalTime(d1: LocalTime?, d2: LocalTime?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        else -> {
            if (d2.nanosecond != 0) {
                assertEquals(d1, d2)
            } else {
                assertEquals(LocalTime(d1.hour, d1.minute, d1.second), LocalTime(d2.hour, d2.minute, d2.second))
            }
        }
    }
}
