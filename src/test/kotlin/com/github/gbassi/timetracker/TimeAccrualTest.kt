package com.github.gbassi.timetracker

import com.github.gbassi.timetracker.model.BranchEntry
import com.github.gbassi.timetracker.service.accrue
import com.github.gbassi.timetracker.util.formatClock
import com.github.gbassi.timetracker.util.formatHuman
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class TimeAccrualTest {

    private val rome = ZoneId.of("Europe/Rome")

    private fun at(y: Int, m: Int, d: Int, hh: Int, mm: Int, ss: Int = 0) =
        ZonedDateTime.of(y, m, d, hh, mm, ss, 0, rome).toInstant()

    @Test
    fun `accrua all'interno dello stesso giorno`() {
        val e = BranchEntry()
        accrue(e, at(2026, 9, 8, 10, 0, 0), at(2026, 9, 8, 10, 30, 0), rome)
        assertEquals(1, e.days.size)
        assertEquals("2026-09-08", e.days[0].date)
        assertEquals(1800L, e.days[0].seconds)
    }

    @Test
    fun `accrua sommando su chiamate successive`() {
        val e = BranchEntry()
        accrue(e, at(2026, 9, 8, 10, 0, 0), at(2026, 9, 8, 10, 10, 0), rome)
        accrue(e, at(2026, 9, 8, 14, 0, 0), at(2026, 9, 8, 14, 5, 0), rome)
        assertEquals(900L, e.secondsOn("2026-09-08"))
    }

    @Test
    fun `accrua a cavallo della mezzanotte divide sui due giorni`() {
        val e = BranchEntry()
        accrue(e, at(2026, 9, 8, 23, 40, 0), at(2026, 9, 9, 0, 20, 0), rome)
        assertEquals(1200L, e.secondsOn("2026-09-08"))
        assertEquals(1200L, e.secondsOn("2026-09-09"))
    }

    @Test
    fun `intervallo nullo o negativo non fa nulla`() {
        val e = BranchEntry()
        val t = at(2026, 9, 8, 10, 0, 0)
        accrue(e, t, t, rome)
        accrue(e, at(2026, 9, 8, 11, 0, 0), at(2026, 9, 8, 10, 0, 0), rome)
        assertEquals(0, e.days.size)
    }

    @Test
    fun `total somma tutti i giorni`() {
        val e = BranchEntry()
        accrue(e, at(2026, 9, 7, 9, 0, 0), at(2026, 9, 7, 10, 0, 0), rome)
        accrue(e, at(2026, 9, 8, 9, 0, 0), at(2026, 9, 8, 9, 30, 0), rome)
        assertEquals(5400L, e.totalSeconds())
    }

    @Test
    fun `formattazione tempo`() {
        assertEquals("0:07:03", formatClock(423))
        assertEquals("12:30:00", formatClock(45000))
        assertEquals("0:00:00", formatClock(-5))
        assertEquals("2h 05m", formatHuman(7500))
        assertEquals("45m", formatHuman(2700))
    }

    @Test
    fun `bucket non duplica per la stessa data`() {
        val e = BranchEntry()
        val today = LocalDate.now().toString()
        e.bucket(today).seconds += 10
        e.bucket(today).seconds += 5
        assertEquals(1, e.days.size)
        assertEquals(15L, e.secondsOn(today))
    }
}
