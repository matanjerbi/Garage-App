package com.matan.garage.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Dates are stored as epoch days (LocalDate.toEpochDay()).

enum class VehicleType { CAR, MOTORCYCLE }
enum class Category { ENGINE, DRIVETRAIN, BRAKES, TIRES, ELECTRICAL, FLUIDS, OTHER }
enum class ServiceType { MAINTENANCE, REPAIR }
enum class PerformedBy { SELF, GARAGE }
enum class ReadingSource { MANUAL, SERVICE, FUEL }
enum class DocType { TEST, COMPULSORY_INSURANCE, COMPREHENSIVE_INSURANCE, VEHICLE_LICENSE, OTHER }
enum class OwnerType { SERVICE_RECORD, DOCUMENT }

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: VehicleType,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val licensePlate: String = "",
    val photoPath: String? = null,
    val trackFuel: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "maintenance_items", indices = [Index("vehicleId")])
data class MaintenanceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val name: String,
    val category: Category,
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    val baselineKm: Int,
    val baselineDate: Long,
    val notes: String = "",
    val active: Boolean = true,
)

@Entity(tableName = "service_records", indices = [Index("vehicleId")])
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val odometerKm: Int,
    val type: ServiceType,
    val performedBy: PerformedBy,
    val garageId: Long? = null,
    val partsCost: Double = 0.0,
    val laborCost: Double = 0.0,
    val notes: String = "",
)

@Entity(
    tableName = "service_record_items",
    primaryKeys = ["serviceRecordId", "maintenanceItemId"],
    indices = [Index("maintenanceItemId")],
)
data class ServiceRecordItem(
    val serviceRecordId: Long,
    val maintenanceItemId: Long,
)

@Entity(tableName = "odometer_readings", indices = [Index("vehicleId")])
data class OdometerReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val km: Int,
    val source: ReadingSource,
    /** id of the service record / fuel entry that created this reading */
    val refId: Long? = null,
)

@Entity(tableName = "fuel_entries", indices = [Index("vehicleId")])
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val odometerKm: Int,
    val liters: Double,
    val totalCost: Double,
    val fullTank: Boolean = true,
)

@Entity(tableName = "documents", indices = [Index("vehicleId")])
data class VehicleDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val type: DocType,
    val expiryDate: Long,
    val cost: Double? = null,
    val notes: String = "",
)

@Entity(tableName = "garages")
data class Garage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
)

@Entity(tableName = "attachments", indices = [Index(value = ["ownerType", "ownerId"])])
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: OwnerType,
    val ownerId: Long,
    /** file name inside the app's media folder */
    val fileName: String,
    val mimeType: String,
)
