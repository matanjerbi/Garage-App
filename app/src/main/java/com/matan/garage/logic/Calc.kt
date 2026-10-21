package com.matan.garage.logic

import com.matan.garage.data.FuelEntry
import com.matan.garage.data.Garage
import com.matan.garage.data.MaintenanceItem
import com.matan.garage.data.OdometerReading
import com.matan.garage.data.ServiceRecord
import com.matan.garage.data.ServiceRecordItem
import com.matan.garage.data.ServiceType
import com.matan.garage.data.Vehicle
import com.matan.garage.data.VehicleDocument
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Everything in the database, small enough to keep in memory and derive all screens from. */
data class Snapshot(
    val vehicles: List<Vehicle> = emptyList(),
    val items: List<MaintenanceItem> = emptyList(),
    val records: List<ServiceRecord> = emptyList(),
    val recordItems: List<ServiceRecordItem> = emptyList(),
    val readings: List<OdometerReading> = emptyList(),
    val fuel: List<FuelEntry> = emptyList(),
    val documents: List<VehicleDocument> = emptyList(),
    val garages: List<Garage> = emptyList(),
) {
    fun vehicle(id: Long) = vehicles.find { it.id == id }
    fun item(id: Long) = items.find { it.id == id }
    fun garage(id: Long?) = if (id == null) null else garages.find { it.id == id }
    fun itemsOf(vid: Long) = items.filter { it.vehicleId == vid }
    fun recordsOf(vid: Long) = records.filter { it.vehicleId == vid }
    fun readingsOf(vid: Long) = readings.filter { it.vehicleId == vid }
    fun fuelOf(vid: Long) = fuel.filter { it.vehicleId == vid }
    fun documentsOf(vid: Long) = documents.filter { it.vehicleId == vid }
    fun itemIdsOf(recordId: Long) =
        recordItems.filter { it.serviceRecordId == recordId }.map { it.maintenanceItemId }

    fun recordsWithItem(itemId: Long): List<ServiceRecord> {
        val ids = recordItems.filter { it.maintenanceItemId == itemId }.map { it.serviceRecordId }.toSet()
        return records.filter { it.id in ids }
    }
}

enum class Status { OVERDUE, DUE_SOON, OK }

data class Thresholds(val soonKm: Int = 500, val soonDays: Int = 14)

data class ItemState(
    val item: MaintenanceItem,
    val lastKm: Int,
    val lastDate: LocalDate,
    /** km left until due (negative = overdue); null if the item has no km interval */
    val kmLeft: Int?,
    /** days left until due (negative = overdue); null if the item has no time interval */
    val daysLeft: Long?,
    val kmProgress: Float?,
    val timeProgress: Float?,
    /** earlier of the time-based due date and the km-based estimate */
    val dueDate: LocalDate?,
    val status: Status,
)

data class FuelStat(val entry: FuelEntry, val kmPerLiter: Double?)

enum class CostCategory { MAINTENANCE, REPAIR, FUEL, DOCUMENTS }

data class CostEntry(val date: LocalDate, val category: CostCategory, val amount: Double)

object Calc {
    private val readingOrder = compareBy<OdometerReading>({ it.date }, { it.id })

    /** Current odometer = the latest reading (by date, then entry order). */
    fun currentKm(readings: List<OdometerReading>): Int =
        readings.maxWithOrNull(readingOrder)?.km ?: 0

    fun lastReadingDate(readings: List<OdometerReading>): LocalDate? =
        readings.maxWithOrNull(readingOrder)?.let { LocalDate.ofEpochDay(it.date) }

    /** Average km/day from the last 60 days of readings (falls back to all readings). */
    fun kmPerDay(readings: List<OdometerReading>, today: LocalDate): Double? {
        if (readings.size < 2) return null
        val recent = readings.filter { it.date >= today.toEpochDay() - 60 }
        val use = (if (recent.size >= 2) recent else readings).sortedWith(readingOrder)
        val days = use.last().date - use.first().date
        val km = use.last().km - use.first().km
        if (days <= 0 || km <= 0) return null
        return km.toDouble() / days
    }

    /** km and date the item was last done: latest service containing it, or the item's baseline. */
    fun lastDone(item: MaintenanceItem, recordsWithItem: List<ServiceRecord>): Pair<Int, LocalDate> {
        val last = recordsWithItem.maxWithOrNull(compareBy({ it.date }, { it.odometerKm }))
        return if (last != null && last.date >= item.baselineDate) {
            last.odometerKm to LocalDate.ofEpochDay(last.date)
        } else {
            item.baselineKm to LocalDate.ofEpochDay(item.baselineDate)
        }
    }

    fun itemState(
        item: MaintenanceItem,
        lastKm: Int,
        lastDate: LocalDate,
        currentKm: Int,
        today: LocalDate,
        kmPerDay: Double?,
        th: Thresholds,
    ): ItemState {
        var kmLeft: Int? = null
        var kmProgress: Float? = null
        var kmDue: LocalDate? = null
        val ivKm = item.intervalKm
        if (ivKm != null && ivKm > 0) {
            val used = (currentKm - lastKm).coerceAtLeast(0)
            val left = ivKm - used
            kmLeft = left
            kmProgress = (used.toFloat() / ivKm).coerceIn(0f, 1f)
            kmDue = when {
                left <= 0 -> today
                kmPerDay != null && kmPerDay > 0 -> today.plusDays(ceil(left / kmPerDay).toLong())
                else -> null
            }
        }

        var daysLeft: Long? = null
        var timeProgress: Float? = null
        var timeDue: LocalDate? = null
        val ivMonths = item.intervalMonths
        if (ivMonths != null && ivMonths > 0) {
            val due = lastDate.plusMonths(ivMonths.toLong())
            val total = ChronoUnit.DAYS.between(lastDate, due).coerceAtLeast(1)
            val left = ChronoUnit.DAYS.between(today, due)
            daysLeft = left
            timeDue = due
            timeProgress = ((total - left).toFloat() / total).coerceIn(0f, 1f)
        }

        val overdue = (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0)
        val soon = (kmLeft != null && kmLeft <= th.soonKm) || (daysLeft != null && daysLeft <= th.soonDays)
        val status = when {
            overdue -> Status.OVERDUE
            soon -> Status.DUE_SOON
            else -> Status.OK
        }
        return ItemState(
            item = item,
            lastKm = lastKm,
            lastDate = lastDate,
            kmLeft = kmLeft,
            daysLeft = daysLeft,
            kmProgress = kmProgress,
            timeProgress = timeProgress,
            dueDate = listOfNotNull(kmDue, timeDue).minOrNull(),
            status = status,
        )
    }

    val urgencyOrder = compareBy<ItemState>(
        { it.status.ordinal },
        { -maxOf(it.kmProgress ?: 0f, it.timeProgress ?: 0f) },
    )

    /** States of all active items of a vehicle, most urgent first. */
    fun vehicleItemStates(snap: Snapshot, vehicleId: Long, today: LocalDate, th: Thresholds): List<ItemState> {
        val readings = snap.readingsOf(vehicleId)
        val current = currentKm(readings)
        val rate = kmPerDay(readings, today)
        return snap.itemsOf(vehicleId).filter { it.active }.map { item ->
            val (km, date) = lastDone(item, snap.recordsWithItem(item.id))
            itemState(item, km, date, current, today, rate, th)
        }.sortedWith(urgencyOrder)
    }

    /** Consumption per full-tank fill (km/L between consecutive full tanks), oldest first. */
    fun fuelStats(entries: List<FuelEntry>): List<FuelStat> {
        val sorted = entries.sortedWith(compareBy({ it.odometerKm }, { it.date }, { it.id }))
        val result = mutableListOf<FuelStat>()
        var lastFullKm: Int? = null
        var liters = 0.0
        for (e in sorted) {
            val prevFull = lastFullKm
            if (prevFull == null) {
                if (e.fullTank) lastFullKm = e.odometerKm
                result += FuelStat(e, null)
                continue
            }
            liters += e.liters
            if (e.fullTank) {
                val distance = e.odometerKm - prevFull
                val kml = if (distance > 0 && liters > 0) distance / liters else null
                result += FuelStat(e, kml)
                lastFullKm = e.odometerKm
                liters = 0.0
            } else {
                result += FuelStat(e, null)
            }
        }
        return result
    }

    /** Average km/L over all closed full-tank segments. */
    fun averageKmPerLiter(entries: List<FuelEntry>): Double? {
        val sorted = entries.sortedWith(compareBy({ it.odometerKm }, { it.date }, { it.id }))
        val firstFull = sorted.indexOfFirst { it.fullTank }
        val lastFull = sorted.indexOfLast { it.fullTank }
        if (firstFull < 0 || lastFull <= firstFull) return null
        val distance = sorted[lastFull].odometerKm - sorted[firstFull].odometerKm
        val liters = sorted.subList(firstFull + 1, lastFull + 1).sumOf { it.liters }
        return if (distance > 0 && liters > 0) distance / liters else null
    }

    fun costEntries(snap: Snapshot, vehicleId: Long): List<CostEntry> {
        val list = mutableListOf<CostEntry>()
        snap.recordsOf(vehicleId).forEach {
            val cat = if (it.type == ServiceType.REPAIR) CostCategory.REPAIR else CostCategory.MAINTENANCE
            list += CostEntry(LocalDate.ofEpochDay(it.date), cat, it.partsCost + it.laborCost)
        }
        snap.fuelOf(vehicleId).forEach {
            list += CostEntry(LocalDate.ofEpochDay(it.date), CostCategory.FUEL, it.totalCost)
        }
        snap.documentsOf(vehicleId).forEach { d ->
            val cost = d.cost
            // documents are paid around a year before they expire
            if (cost != null) list += CostEntry(LocalDate.ofEpochDay(d.expiryDate).minusYears(1), CostCategory.DOCUMENTS, cost)
        }
        return list.filter { it.amount > 0 }
    }

    /** Totals for the last [months] months, oldest first. */
    fun monthlyTotals(entries: List<CostEntry>, today: LocalDate, months: Int = 12): List<Pair<YearMonth, Double>> {
        val now = YearMonth.from(today)
        return (months - 1 downTo 0).map { back ->
            val ym = now.minusMonths(back.toLong())
            ym to entries.filter { YearMonth.from(it.date) == ym }.sumOf { it.amount }
        }
    }

    /** Distance driven since the first reading, used for cost per km. */
    fun kmTracked(readings: List<OdometerReading>): Int {
        if (readings.isEmpty()) return 0
        val first = readings.minWithOrNull(readingOrder)!!.km
        return (currentKm(readings) - first).coerceAtLeast(0)
    }
}
