package com.matan.garage.logic

import com.matan.garage.data.Category
import com.matan.garage.data.FuelEntry
import com.matan.garage.data.MaintenanceItem
import com.matan.garage.data.OdometerReading
import com.matan.garage.data.PerformedBy
import com.matan.garage.data.ReadingSource
import com.matan.garage.data.ServiceRecord
import com.matan.garage.data.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalcTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val th = Thresholds(soonKm = 500, soonDays = 14)

    private fun item(km: Int? = null, months: Int? = null, baseKm: Int = 10_000, baseDate: LocalDate = today) =
        MaintenanceItem(
            id = 1, vehicleId = 1, name = "oil", category = Category.ENGINE,
            intervalKm = km, intervalMonths = months, baselineKm = baseKm, baselineDate = baseDate.toEpochDay(),
        )

    private fun state(i: MaintenanceItem, currentKm: Int, rate: Double? = null): ItemState {
        val (km, date) = Calc.lastDone(i, emptyList())
        return Calc.itemState(i, km, date, currentKm, today, rate, th)
    }

    @Test fun kmOnly_ok() {
        val s = state(item(km = 6000), currentKm = 12_000)
        assertEquals(Status.OK, s.status)
        assertEquals(4000, s.kmLeft)
        assertNull(s.daysLeft)
    }

    @Test fun kmOnly_dueSoon() {
        assertEquals(Status.DUE_SOON, state(item(km = 6000), currentKm = 15_600).status)
    }

    @Test fun kmOnly_overdueAtExactInterval() {
        val s = state(item(km = 6000), currentKm = 16_000)
        assertEquals(Status.OVERDUE, s.status)
        assertEquals(0, s.kmLeft)
    }

    @Test fun timeOnly() {
        val ok = state(item(months = 12, baseDate = today.minusMonths(6)), currentKm = 10_000)
        assertEquals(Status.OK, ok.status)
        assertNull(ok.kmLeft)

        val soon = state(item(months = 12, baseDate = today.minusMonths(12).plusDays(10)), currentKm = 10_000)
        assertEquals(Status.DUE_SOON, soon.status)
        assertEquals(10L, soon.daysLeft)

        val over = state(item(months = 12, baseDate = today.minusMonths(13)), currentKm = 10_000)
        assertEquals(Status.OVERDUE, over.status)
    }

    @Test fun both_whicheverComesFirst() {
        // km is fine, but time is overdue
        val s = state(item(km = 6000, months = 12, baseDate = today.minusMonths(14)), currentKm = 11_000)
        assertEquals(Status.OVERDUE, s.status)
        // time is fine, km due soon
        val s2 = state(item(km = 6000, months = 12, baseDate = today.minusMonths(1)), currentKm = 15_800)
        assertEquals(Status.DUE_SOON, s2.status)
    }

    @Test fun neverDone_usesBaseline() {
        val i = item(km = 1000, baseKm = 5000)
        val (km, date) = Calc.lastDone(i, emptyList())
        assertEquals(5000, km)
        assertEquals(today, date)
    }

    @Test fun lastDone_usesLatestServiceAfterBaseline() {
        val i = item(km = 1000, baseKm = 5000, baseDate = today.minusDays(100))
        val r = ServiceRecord(
            id = 7, vehicleId = 1, date = today.minusDays(10).toEpochDay(), odometerKm = 8000,
            type = ServiceType.MAINTENANCE, performedBy = PerformedBy.SELF,
        )
        assertEquals(8000 to today.minusDays(10), Calc.lastDone(i, listOf(r)))
    }

    @Test fun estimatedDate_fromRidingRate() {
        val s = state(item(km = 1000), currentKm = 10_000, rate = 50.0)
        assertEquals(today.plusDays(20), s.dueDate)
    }

    @Test fun dueDate_isEarlierOfKmAndTime() {
        val s = state(item(km = 10_000, months = 1), currentKm = 10_000, rate = 10.0)
        assertEquals(today.plusMonths(1), s.dueDate)
    }

    @Test fun kmPerDay_recentReadings() {
        val r = listOf(
            OdometerReading(1, 1, today.minusDays(30).toEpochDay(), 1000, ReadingSource.MANUAL),
            OdometerReading(2, 1, today.toEpochDay(), 2500, ReadingSource.MANUAL),
        )
        assertEquals(50.0, Calc.kmPerDay(r, today)!!, 0.001)
        assertNull(Calc.kmPerDay(r.take(1), today))
    }

    @Test fun currentKm_isLatestReading() {
        val r = listOf(
            OdometerReading(1, 1, today.minusDays(5).toEpochDay(), 99_000, ReadingSource.MANUAL), // typo
            OdometerReading(2, 1, today.toEpochDay(), 9_900, ReadingSource.MANUAL), // fix
        )
        assertEquals(9_900, Calc.currentKm(r))
    }

    @Test fun fuelConsumption_betweenFullTanks() {
        val d = today.toEpochDay()
        val f = listOf(
            FuelEntry(1, 1, d, 1000, 10.0, 70.0, true),
            FuelEntry(2, 1, d, 1100, 5.0, 35.0, false),
            FuelEntry(3, 1, d, 1300, 10.0, 70.0, true),
        )
        val stats = Calc.fuelStats(f)
        assertNull(stats[0].kmPerLiter)
        assertNull(stats[1].kmPerLiter)
        assertEquals(20.0, stats[2].kmPerLiter!!, 0.001)
        assertEquals(20.0, Calc.averageKmPerLiter(f)!!, 0.001)
    }
}
