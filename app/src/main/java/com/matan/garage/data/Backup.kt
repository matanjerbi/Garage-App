package com.matan.garage.data

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup = one zip: data.json (all tables) + media/ (all attached files). */
object Backup {
    private const val DATA = "data.json"
    private const val MEDIA = "media/"

    suspend fun export(context: Context, repo: Repo, out: OutputStream) {
        val d = repo.dao
        val json = JSONObject()
            .put("version", 1)
            .put("vehicles", arr(d.allVehicles()) { it.toJson() })
            .put("items", arr(d.allItems()) { it.toJson() })
            .put("records", arr(d.allRecords()) { it.toJson() })
            .put("recordItems", arr(d.allRecordItems()) { it.toJson() })
            .put("readings", arr(d.allReadings()) { it.toJson() })
            .put("fuel", arr(d.allFuel()) { it.toJson() })
            .put("documents", arr(d.allDocuments()) { it.toJson() })
            .put("garages", arr(d.allGarages()) { it.toJson() })
            .put("attachments", arr(d.allAttachments()) { it.toJson() })

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            Media.dir(context).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry(MEDIA + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Replaces ALL current data with the backup. Throws if the file isn't a valid backup. */
    suspend fun import(context: Context, repo: Repo, input: InputStream) {
        val tmp = File(context.filesDir, "media_import").apply { deleteRecursively(); mkdirs() }
        var jsonText: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == DATA -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith(MEDIA) && !entry.isDirectory -> {
                        val name = File(entry.name).name // strip any path parts
                        File(tmp, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val json = JSONObject(jsonText ?: run { tmp.deleteRecursively(); error("קובץ הגיבוי לא תקין") })
        val d = repo.dao
        repo.db.withTransaction {
            d.clearRecordItems(); d.clearAttachments(); d.clearRecords(); d.clearItems(); d.clearReadings()
            d.clearFuel(); d.clearDocuments(); d.clearGarages(); d.clearVehicles()
            d.insertVehicles(list(json, "vehicles") { vehicle(it) })
            d.insertItems(list(json, "items") { item(it) })
            d.insertRecords(list(json, "records") { record(it) })
            d.insertRecordItems(list(json, "recordItems") { ServiceRecordItem(it.getLong("serviceRecordId"), it.getLong("maintenanceItemId")) })
            d.insertReadings(list(json, "readings") { reading(it) })
            d.insertFuel(list(json, "fuel") { fuel(it) })
            d.insertDocuments(list(json, "documents") { document(it) })
            d.insertGarages(list(json, "garages") { Garage(it.getLong("id"), it.getString("name"), it.str("phone"), it.str("address")) })
            d.insertAttachments(list(json, "attachments") { attachment(it) })
        }
        val media = Media.dir(context)
        media.deleteRecursively()
        if (!tmp.renameTo(media)) {
            media.mkdirs()
            tmp.listFiles()?.forEach { it.copyTo(File(media, it.name), overwrite = true) }
            tmp.deleteRecursively()
        }
    }

    // ---- helpers ----
    private fun <T> arr(items: List<T>, f: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(f(it)) } }

    private fun <T> list(json: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = json.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    private fun JSONObject.str(k: String) = if (isNull(k)) "" else getString(k)
    private fun JSONObject.strOrNull(k: String) = if (isNull(k)) null else getString(k)
    private fun JSONObject.intOrNull(k: String) = if (isNull(k)) null else getInt(k)
    private fun JSONObject.longOrNull(k: String) = if (isNull(k)) null else getLong(k)
    private fun JSONObject.doubleOrNull(k: String) = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.putN(k: String, v: Any?) = put(k, v ?: JSONObject.NULL)

    private fun Vehicle.toJson() = JSONObject().put("id", id).put("name", name).put("type", type.name)
        .put("make", make).put("model", model).putN("year", year).put("licensePlate", licensePlate)
        .putN("photoPath", photoPath).put("trackFuel", trackFuel).put("archived", archived).put("createdAt", createdAt)

    private fun vehicle(o: JSONObject) = Vehicle(
        o.getLong("id"), o.getString("name"), VehicleType.valueOf(o.getString("type")), o.str("make"), o.str("model"),
        o.intOrNull("year"), o.str("licensePlate"), o.strOrNull("photoPath"), o.optBoolean("trackFuel"),
        o.optBoolean("archived"), o.optLong("createdAt"),
    )

    private fun MaintenanceItem.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("name", name)
        .put("category", category.name).putN("intervalKm", intervalKm).putN("intervalMonths", intervalMonths)
        .put("baselineKm", baselineKm).put("baselineDate", baselineDate).put("notes", notes).put("active", active)

    private fun item(o: JSONObject) = MaintenanceItem(
        o.getLong("id"), o.getLong("vehicleId"), o.getString("name"), Category.valueOf(o.getString("category")),
        o.intOrNull("intervalKm"), o.intOrNull("intervalMonths"), o.getInt("baselineKm"), o.getLong("baselineDate"),
        o.str("notes"), o.optBoolean("active", true),
    )

    private fun ServiceRecord.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("date", date)
        .put("odometerKm", odometerKm).put("type", type.name).put("performedBy", performedBy.name)
        .putN("garageId", garageId).put("partsCost", partsCost).put("laborCost", laborCost).put("notes", notes)

    private fun record(o: JSONObject) = ServiceRecord(
        o.getLong("id"), o.getLong("vehicleId"), o.getLong("date"), o.getInt("odometerKm"),
        ServiceType.valueOf(o.getString("type")), PerformedBy.valueOf(o.getString("performedBy")),
        o.longOrNull("garageId"), o.optDouble("partsCost", 0.0), o.optDouble("laborCost", 0.0), o.str("notes"),
    )

    private fun ServiceRecordItem.toJson() =
        JSONObject().put("serviceRecordId", serviceRecordId).put("maintenanceItemId", maintenanceItemId)

    private fun OdometerReading.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("date", date)
        .put("km", km).put("source", source.name).putN("refId", refId)

    private fun reading(o: JSONObject) = OdometerReading(
        o.getLong("id"), o.getLong("vehicleId"), o.getLong("date"), o.getInt("km"),
        ReadingSource.valueOf(o.getString("source")), o.longOrNull("refId"),
    )

    private fun FuelEntry.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("date", date)
        .put("odometerKm", odometerKm).put("liters", liters).put("totalCost", totalCost).put("fullTank", fullTank)

    private fun fuel(o: JSONObject) = FuelEntry(
        o.getLong("id"), o.getLong("vehicleId"), o.getLong("date"), o.getInt("odometerKm"),
        o.getDouble("liters"), o.getDouble("totalCost"), o.optBoolean("fullTank", true),
    )

    private fun VehicleDocument.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("type", type.name)
        .put("expiryDate", expiryDate).putN("cost", cost).put("notes", notes)

    private fun document(o: JSONObject) = VehicleDocument(
        o.getLong("id"), o.getLong("vehicleId"), DocType.valueOf(o.getString("type")), o.getLong("expiryDate"),
        o.doubleOrNull("cost"), o.str("notes"),
    )

    private fun Garage.toJson() = JSONObject().put("id", id).put("name", name).put("phone", phone).put("address", address)

    private fun Attachment.toJson() = JSONObject().put("id", id).put("ownerType", ownerType.name).put("ownerId", ownerId)
        .put("fileName", fileName).put("mimeType", mimeType)

    private fun attachment(o: JSONObject) = Attachment(
        o.getLong("id"), OwnerType.valueOf(o.getString("ownerType")), o.getLong("ownerId"),
        o.getString("fileName"), o.getString("mimeType"),
    )
}
