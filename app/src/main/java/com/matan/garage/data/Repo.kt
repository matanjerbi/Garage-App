package com.matan.garage.data

import android.content.Context
import androidx.room.withTransaction
import com.matan.garage.logic.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

class Repo(private val context: Context, val db: AppDatabase) {
    val dao = db.dao()

    private data class Part(
        val v: List<Vehicle>, val i: List<MaintenanceItem>, val r: List<ServiceRecord>,
        val ri: List<ServiceRecordItem>, val o: List<OdometerReading>,
    )

    fun snapshot(): Flow<Snapshot> = combine(
        combine(dao.vehicles(), dao.items(), dao.records(), dao.recordItems(), dao.readings()) { v, i, r, ri, o ->
            Part(v, i, r, ri, o)
        },
        dao.fuel(), dao.documents(), dao.garages(),
    ) { p, f, d, g -> Snapshot(p.v, p.i, p.r, p.ri, p.o, f, d, g) }

    suspend fun attachmentsOf(type: OwnerType, ownerId: Long) = dao.attachments(type.name, ownerId)

    // ---- vehicles ----
    suspend fun saveVehicle(v: Vehicle, initialKm: Int, withTemplate: Boolean): Long = db.withTransaction {
        val isNew = v.id == 0L
        val id = dao.saveVehicle(v)
        if (isNew) {
            val today = LocalDate.now().toEpochDay()
            dao.saveReading(OdometerReading(vehicleId = id, date = today, km = initialKm, source = ReadingSource.MANUAL))
            if (withTemplate) {
                Templates.forType(v.type).forEach {
                    dao.saveItem(
                        MaintenanceItem(
                            vehicleId = id, name = it.name, category = it.category,
                            intervalKm = it.km, intervalMonths = it.months,
                            baselineKm = initialKm, baselineDate = today,
                        )
                    )
                }
            }
        }
        id
    }

    suspend fun updateVehicle(v: Vehicle) {
        dao.saveVehicle(v)
    }

    suspend fun deleteVehicle(v: Vehicle) = db.withTransaction {
        dao.recordsOf(v.id).forEach { removeAttachments(OwnerType.SERVICE_RECORD, it.id) }
        dao.documentsOf(v.id).forEach { removeAttachments(OwnerType.DOCUMENT, it.id) }
        dao.deleteRecordItemsOf(v.id)
        dao.deleteRecordsOf(v.id)
        dao.deleteItemsOf(v.id)
        dao.deleteReadingsOf(v.id)
        dao.deleteFuelOf(v.id)
        dao.deleteDocumentsOf(v.id)
        dao.deleteVehicle(v.id)
        v.photoPath?.let { Media.file(context, it).delete() }
    }

    suspend fun addReading(vehicleId: Long, km: Int, date: LocalDate = LocalDate.now()) {
        dao.saveReading(OdometerReading(vehicleId = vehicleId, date = date.toEpochDay(), km = km, source = ReadingSource.MANUAL))
    }

    // ---- maintenance items ----
    suspend fun saveItem(i: MaintenanceItem) = dao.saveItem(i)

    suspend fun deleteItem(id: Long) = db.withTransaction {
        dao.deleteRecordItemsForItem(id)
        dao.deleteItem(id)
    }

    // ---- service records ----
    suspend fun saveService(r: ServiceRecord, itemIds: Set<Long>, files: List<Attachment>): Long = db.withTransaction {
        val id = dao.saveRecord(r)
        dao.deleteRecordItems(id)
        itemIds.forEach { dao.saveRecordItem(ServiceRecordItem(id, it)) }
        dao.deleteReadingsByRef(ReadingSource.SERVICE.name, id)
        dao.saveReading(
            OdometerReading(vehicleId = r.vehicleId, date = r.date, km = r.odometerKm, source = ReadingSource.SERVICE, refId = id)
        )
        replaceAttachments(OwnerType.SERVICE_RECORD, id, files)
        id
    }

    suspend fun deleteService(id: Long) = db.withTransaction {
        dao.deleteRecordItems(id)
        dao.deleteReadingsByRef(ReadingSource.SERVICE.name, id)
        removeAttachments(OwnerType.SERVICE_RECORD, id)
        dao.deleteRecord(id)
    }

    // ---- fuel ----
    suspend fun saveFuel(f: FuelEntry) = db.withTransaction {
        val id = dao.saveFuel(f)
        dao.deleteReadingsByRef(ReadingSource.FUEL.name, id)
        dao.saveReading(
            OdometerReading(vehicleId = f.vehicleId, date = f.date, km = f.odometerKm, source = ReadingSource.FUEL, refId = id)
        )
    }

    suspend fun deleteFuel(id: Long) = db.withTransaction {
        dao.deleteReadingsByRef(ReadingSource.FUEL.name, id)
        dao.deleteFuel(id)
    }

    // ---- documents ----
    suspend fun saveDocument(d: VehicleDocument, files: List<Attachment>) = db.withTransaction {
        val id = dao.saveDocument(d)
        replaceAttachments(OwnerType.DOCUMENT, id, files)
    }

    suspend fun deleteDocument(id: Long) = db.withTransaction {
        removeAttachments(OwnerType.DOCUMENT, id)
        dao.deleteDocument(id)
    }

    // ---- garages ----
    suspend fun saveGarage(g: Garage) = dao.saveGarage(g)

    suspend fun deleteGarage(id: Long) = db.withTransaction {
        dao.clearGarage(id)
        dao.deleteGarage(id)
    }

    // ---- attachments ----
    private suspend fun replaceAttachments(type: OwnerType, ownerId: Long, files: List<Attachment>) {
        val keep = files.map { it.fileName }.toSet()
        dao.attachments(type.name, ownerId).filter { it.fileName !in keep }
            .forEach { Media.file(context, it.fileName).delete() }
        dao.deleteAttachments(type.name, ownerId)
        files.forEach { dao.saveAttachment(it.copy(id = 0, ownerType = type, ownerId = ownerId)) }
    }

    private suspend fun removeAttachments(type: OwnerType, ownerId: Long) {
        dao.attachments(type.name, ownerId).forEach { Media.file(context, it.fileName).delete() }
        dao.deleteAttachments(type.name, ownerId)
    }
}
