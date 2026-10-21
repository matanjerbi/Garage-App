package com.matan.garage.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        Vehicle::class, MaintenanceItem::class, ServiceRecord::class, ServiceRecordItem::class,
        OdometerReading::class, FuelEntry::class, VehicleDocument::class, Garage::class, Attachment::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GarageDao
}

@Dao
interface GarageDao {
    // ---- observed tables ----
    @Query("SELECT * FROM vehicles ORDER BY id") fun vehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM maintenance_items ORDER BY id") fun items(): Flow<List<MaintenanceItem>>
    @Query("SELECT * FROM service_records ORDER BY date DESC, id DESC") fun records(): Flow<List<ServiceRecord>>
    @Query("SELECT * FROM service_record_items") fun recordItems(): Flow<List<ServiceRecordItem>>
    @Query("SELECT * FROM odometer_readings ORDER BY date, id") fun readings(): Flow<List<OdometerReading>>
    @Query("SELECT * FROM fuel_entries ORDER BY date DESC, id DESC") fun fuel(): Flow<List<FuelEntry>>
    @Query("SELECT * FROM documents ORDER BY expiryDate") fun documents(): Flow<List<VehicleDocument>>
    @Query("SELECT * FROM garages ORDER BY name") fun garages(): Flow<List<Garage>>

    // ---- one-shot reads ----
    @Query("SELECT * FROM vehicles") suspend fun allVehicles(): List<Vehicle>
    @Query("SELECT * FROM maintenance_items") suspend fun allItems(): List<MaintenanceItem>
    @Query("SELECT * FROM service_records") suspend fun allRecords(): List<ServiceRecord>
    @Query("SELECT * FROM service_record_items") suspend fun allRecordItems(): List<ServiceRecordItem>
    @Query("SELECT * FROM odometer_readings") suspend fun allReadings(): List<OdometerReading>
    @Query("SELECT * FROM fuel_entries") suspend fun allFuel(): List<FuelEntry>
    @Query("SELECT * FROM documents") suspend fun allDocuments(): List<VehicleDocument>
    @Query("SELECT * FROM garages") suspend fun allGarages(): List<Garage>
    @Query("SELECT * FROM attachments") suspend fun allAttachments(): List<Attachment>
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :ownerId")
    suspend fun attachments(type: String, ownerId: Long): List<Attachment>
    @Query("SELECT * FROM service_records WHERE vehicleId = :vid") suspend fun recordsOf(vid: Long): List<ServiceRecord>
    @Query("SELECT * FROM documents WHERE vehicleId = :vid") suspend fun documentsOf(vid: Long): List<VehicleDocument>

    // ---- save (insert or replace) ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveVehicle(v: Vehicle): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveItem(i: MaintenanceItem): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRecord(r: ServiceRecord): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRecordItem(ri: ServiceRecordItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveReading(r: OdometerReading): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFuel(f: FuelEntry): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDocument(d: VehicleDocument): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGarage(g: Garage): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAttachment(a: Attachment): Long

    // ---- bulk insert (backup import) ----
    @Insert suspend fun insertVehicles(l: List<Vehicle>)
    @Insert suspend fun insertItems(l: List<MaintenanceItem>)
    @Insert suspend fun insertRecords(l: List<ServiceRecord>)
    @Insert suspend fun insertRecordItems(l: List<ServiceRecordItem>)
    @Insert suspend fun insertReadings(l: List<OdometerReading>)
    @Insert suspend fun insertFuel(l: List<FuelEntry>)
    @Insert suspend fun insertDocuments(l: List<VehicleDocument>)
    @Insert suspend fun insertGarages(l: List<Garage>)
    @Insert suspend fun insertAttachments(l: List<Attachment>)

    // ---- delete ----
    @Query("DELETE FROM vehicles WHERE id = :id") suspend fun deleteVehicle(id: Long)
    @Query("DELETE FROM maintenance_items WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("DELETE FROM maintenance_items WHERE vehicleId = :vid") suspend fun deleteItemsOf(vid: Long)
    @Query("DELETE FROM service_records WHERE id = :id") suspend fun deleteRecord(id: Long)
    @Query("DELETE FROM service_records WHERE vehicleId = :vid") suspend fun deleteRecordsOf(vid: Long)
    @Query("DELETE FROM service_record_items WHERE serviceRecordId = :rid") suspend fun deleteRecordItems(rid: Long)
    @Query("DELETE FROM service_record_items WHERE maintenanceItemId = :iid") suspend fun deleteRecordItemsForItem(iid: Long)
    @Query("DELETE FROM service_record_items WHERE serviceRecordId IN (SELECT id FROM service_records WHERE vehicleId = :vid)")
    suspend fun deleteRecordItemsOf(vid: Long)
    @Query("DELETE FROM odometer_readings WHERE source = :source AND refId = :refId")
    suspend fun deleteReadingsByRef(source: String, refId: Long)
    @Query("DELETE FROM odometer_readings WHERE vehicleId = :vid") suspend fun deleteReadingsOf(vid: Long)
    @Query("DELETE FROM fuel_entries WHERE id = :id") suspend fun deleteFuel(id: Long)
    @Query("DELETE FROM fuel_entries WHERE vehicleId = :vid") suspend fun deleteFuelOf(vid: Long)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: Long)
    @Query("DELETE FROM documents WHERE vehicleId = :vid") suspend fun deleteDocumentsOf(vid: Long)
    @Query("DELETE FROM garages WHERE id = :id") suspend fun deleteGarage(id: Long)
    @Query("UPDATE service_records SET garageId = NULL WHERE garageId = :id") suspend fun clearGarage(id: Long)
    @Query("DELETE FROM attachments WHERE ownerType = :type AND ownerId = :ownerId")
    suspend fun deleteAttachments(type: String, ownerId: Long)

    // ---- clear all (backup import) ----
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()
    @Query("DELETE FROM maintenance_items") suspend fun clearItems()
    @Query("DELETE FROM service_records") suspend fun clearRecords()
    @Query("DELETE FROM service_record_items") suspend fun clearRecordItems()
    @Query("DELETE FROM odometer_readings") suspend fun clearReadings()
    @Query("DELETE FROM fuel_entries") suspend fun clearFuel()
    @Query("DELETE FROM documents") suspend fun clearDocuments()
    @Query("DELETE FROM garages") suspend fun clearGarages()
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
}
