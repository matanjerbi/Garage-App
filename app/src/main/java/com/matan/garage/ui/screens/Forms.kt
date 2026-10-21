package com.matan.garage.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.garage.data.Attachment
import com.matan.garage.data.Category
import com.matan.garage.data.DocType
import com.matan.garage.data.FuelEntry
import com.matan.garage.data.Garage
import com.matan.garage.data.MaintenanceItem
import com.matan.garage.data.Media
import com.matan.garage.data.OwnerType
import com.matan.garage.data.PerformedBy
import com.matan.garage.data.ReadingSource
import com.matan.garage.data.ServiceRecord
import com.matan.garage.data.ServiceType
import com.matan.garage.data.Vehicle
import com.matan.garage.data.VehicleDocument
import com.matan.garage.data.VehicleType
import com.matan.garage.logic.Calc
import com.matan.garage.ui.AppViewModel
import com.matan.garage.ui.AttachmentsEditor
import com.matan.garage.ui.ChoiceChips
import com.matan.garage.ui.DateField
import com.matan.garage.ui.Dropdown
import com.matan.garage.ui.KM
import com.matan.garage.ui.Loading
import com.matan.garage.ui.NumberField
import com.matan.garage.ui.Routes
import com.matan.garage.ui.SwitchRow
import com.matan.garage.ui.TextInput
import com.matan.garage.ui.label
import com.matan.garage.ui.lowerKmGuard
import com.matan.garage.ui.money
import com.matan.garage.ui.str
import com.matan.garage.ui.toDoubleOrNullClean
import com.matan.garage.ui.toIntOrNullClean
import kotlinx.coroutines.launch
import java.time.LocalDate

// Each screen waits for the snapshot, then a form composable initialises its state once from it.

// ======================= Vehicle =======================

@Composable
fun VehicleEditScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val existing = snap.vehicle(id)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: VehicleType.MOTORCYCLE) }
    var make by remember { mutableStateOf(existing?.make ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var year by remember { mutableStateOf(existing?.year.str()) }
    var plate by remember { mutableStateOf(existing?.licensePlate ?: "") }
    var photo by remember { mutableStateOf(existing?.photoPath) }
    var trackFuel by remember { mutableStateOf(existing?.trackFuel ?: false) }
    var initialKm by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(true) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Media.importUri(context, uri)?.let { photo = it.fileName }
    }

    val isNew = existing == null
    FormScaffold(
        title = if (isNew) "כלי רכב חדש" else "עריכת ${existing?.name}",
        nav = nav,
        saveEnabled = name.isNotBlank() && (!isNew || initialKm.toIntOrNullClean() != null),
        onSave = {
            val v = Vehicle(
                id = existing?.id ?: 0, name = name.trim(), type = type, make = make.trim(), model = model.trim(),
                year = year.toIntOrNullClean(), licensePlate = plate.trim(), photoPath = photo, trackFuel = trackFuel,
                archived = existing?.archived ?: false, createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            if (existing?.photoPath != null && existing.photoPath != photo) Media.file(context, existing.photoPath).delete()
            scope.launch {
                val newId = vm.repo.saveVehicle(v, initialKm.toIntOrNullClean() ?: 0, isNew && template)
                if (isNew) nav.navigate(Routes.vehicle(newId)) { popUpTo(Routes.HOME) } else nav.popBackStack()
            }
        },
    ) {
        ChoiceChips(VehicleType.entries, type, { it.label() }, { type = it })
        TextInput("שם / כינוי", name, { name = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput("יצרן", make, { make = it }, Modifier.weight(1f))
            TextInput("דגם", model, { model = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("שנה", year, { year = it }, Modifier.weight(1f))
            TextInput("מספר רישוי", plate, { plate = it }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing != null || photo != null) {
                VehicleAvatar(Vehicle(name = name, type = type, photoPath = photo), 64)
            }
            OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (photo == null) "הוסף תמונה" else "החלף תמונה") }
            if (photo != null) TextButton(onClick = { photo = null }) { Text("הסר") }
        }
        SwitchRow("מעקב תדלוקים וצריכת דלק", trackFuel) { trackFuel = it }
        if (isNew) {
            NumberField("$KM נוכחי", initialKm, { initialKm = it })
            SwitchRow("הוסף תוכנית תחזוקה מוכנה", template) { template = it }
            if (template) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "התוכנית כוללת פריטים נפוצים עם מרווחים כלליים בלבד. אחרי השמירה כדאי לעבור עליהם ולהתאים אותם לפי ספר הרכב, " +
                            "ולעדכן בכל פריט מתי בוצע לאחרונה.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ======================= Maintenance item =======================

@Composable
fun ItemEditScreen(vm: AppViewModel, nav: NavController, vehicleId: Long, id: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val existing = snap.item(id)
    val scope = rememberCoroutineScope()
    val currentKm = Calc.currentKm(snap.readingsOf(vehicleId))

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: Category.ENGINE) }
    var intervalKm by remember { mutableStateOf(existing?.intervalKm.str()) }
    var intervalMonths by remember { mutableStateOf(existing?.intervalMonths.str()) }
    var baseKm by remember { mutableStateOf((existing?.baselineKm ?: currentKm).toString()) }
    var baseDate by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.baselineDate) } ?: LocalDate.now()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }

    val hasInterval = intervalKm.toIntOrNullClean() != null || intervalMonths.toIntOrNullClean() != null
    FormScaffold(
        title = if (existing == null) "פריט תחזוקה חדש" else "עריכת פריט",
        nav = nav,
        saveEnabled = name.isNotBlank() && baseKm.toIntOrNullClean() != null,
        onSave = {
            val item = MaintenanceItem(
                id = existing?.id ?: 0, vehicleId = vehicleId, name = name.trim(), category = category,
                intervalKm = intervalKm.toIntOrNullClean()?.takeIf { it > 0 },
                intervalMonths = intervalMonths.toIntOrNullClean()?.takeIf { it > 0 },
                baselineKm = baseKm.toIntOrNullClean() ?: 0, baselineDate = baseDate.toEpochDay(),
                notes = notes.trim(), active = active,
            )
            scope.launch { vm.repo.saveItem(item); nav.popBackStack() }
        },
        onDelete = if (existing == null) null else { { scope.launch { vm.repo.deleteItem(existing.id) }; nav.popBackStack() } },
        deleteText = "למחוק את הפריט? טיפולים קיימים יישארו בהיסטוריה.",
    ) {
        TextInput("שם", name, { name = it })
        Dropdown("קטגוריה", Category.entries, category, { it.label() }, { category = it })
        Text("מרווח - מה שמגיע קודם", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("כל כמה $KM", intervalKm, { intervalKm = it }, Modifier.weight(1f))
            NumberField("כל כמה חודשים", intervalMonths, { intervalMonths = it }, Modifier.weight(1f))
        }
        if (!hasInterval) Text("בלי מרווח הפריט לא ייתן תזכורות", style = MaterialTheme.typography.bodySmall)
        Text("בוצע לאחרונה", style = MaterialTheme.typography.titleSmall)
        Text(
            "נקודת ההתחלה לחישוב, אם עוד לא תיעדת טיפול לפריט הזה. טיפול שתתעד יחליף אותה.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(KM, baseKm, { baseKm = it }, Modifier.weight(1f))
            DateField("תאריך", baseDate, { baseDate = it }, Modifier.weight(1f))
        }
        TextInput("הערות (סוג שמן, מק\"ט, כלים...)", notes, { notes = it }, multiline = true)
        SwitchRow("פעיל (כולל תזכורות)", active) { active = it }
    }
}

// ======================= Service record =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceEditScreen(vm: AppViewModel, nav: NavController, vehicleId: Long, id: Long, preItem: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val existing = snap.records.find { it.id == id }
    val scope = rememberCoroutineScope()
    // current km, ignoring this record's own reading
    val currentKm = Calc.currentKm(snap.readingsOf(vehicleId).filter { !(it.source == ReadingSource.SERVICE && it.refId == id) })
    val vehicleItems = snap.itemsOf(vehicleId)

    var date by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.date) } ?: LocalDate.now()) }
    var kmText by remember { mutableStateOf((existing?.odometerKm ?: currentKm).toString()) }
    var type by remember { mutableStateOf(existing?.type ?: ServiceType.MAINTENANCE) }
    var selected by remember {
        mutableStateOf(if (existing != null) snap.itemIdsOf(existing.id).toSet() else setOfNotNull(preItem.takeIf { it > 0 }))
    }
    var performer by remember { mutableStateOf(existing?.performedBy ?: PerformedBy.SELF) }
    var garageId by remember { mutableStateOf(existing?.garageId) }
    var parts by remember { mutableStateOf(existing?.partsCost?.takeIf { it > 0 }.str()) }
    var labor by remember { mutableStateOf(existing?.laborCost?.takeIf { it > 0 }.str()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var files by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var confirmedLow by remember { mutableStateOf(false) }
    var addGarage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (existing != null) files = vm.repo.attachmentsOf(OwnerType.SERVICE_RECORD, existing.id) }

    val kmValue = kmText.toIntOrNullClean()
    val checkKm = if (date >= LocalDate.now()) kmValue else null
    val kmOk = checkKm == null || checkKm >= currentKm || confirmedLow
    FormScaffold(
        title = if (existing == null) "טיפול חדש" else "עריכת טיפול",
        nav = nav,
        saveEnabled = kmValue != null && (type == ServiceType.REPAIR || selected.isNotEmpty()) && kmOk,
        onSave = {
            val r = ServiceRecord(
                id = existing?.id ?: 0, vehicleId = vehicleId, date = date.toEpochDay(), odometerKm = kmValue ?: 0,
                type = type, performedBy = performer, garageId = if (performer == PerformedBy.GARAGE) garageId else null,
                partsCost = parts.toDoubleOrNullClean() ?: 0.0, laborCost = labor.toDoubleOrNullClean() ?: 0.0,
                notes = notes.trim(),
            )
            scope.launch { vm.repo.saveService(r, selected, files); nav.popBackStack() }
        },
        onDelete = if (existing == null) null else { { scope.launch { vm.repo.deleteService(existing.id) }; nav.popBackStack() } },
        deleteText = "למחוק את הטיפול?",
    ) {
        ChoiceChips(ServiceType.entries, type, { if (it == ServiceType.MAINTENANCE) "טיפול" else "תיקון" }, { type = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("תאריך", date, { date = it }, Modifier.weight(1f))
            NumberField(KM, kmText, { kmText = it }, Modifier.weight(1f))
        }
        lowerKmGuard(checkKm, currentKm, confirmedLow) { confirmedLow = it }

        Text(
            if (type == ServiceType.REPAIR) "פריטים שהוחלפו (לא חובה)" else "מה בוצע",
            style = MaterialTheme.typography.titleSmall,
        )
        if (vehicleItems.isEmpty()) Text("אין פריטי תחזוקה לכלי הרכב", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vehicleItems.forEach { item ->
                FilterChip(
                    selected = item.id in selected,
                    onClick = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                    label = { Text(item.name) },
                )
            }
        }

        Text("מי ביצע", style = MaterialTheme.typography.titleSmall)
        ChoiceChips(PerformedBy.entries, performer, { if (it == PerformedBy.SELF) "אני" else "מוסך" }, { performer = it })
        if (performer == PerformedBy.GARAGE) {
            val options = listOf<Long?>(null) + snap.garages.map { it.id } + listOf(-1L)
            Dropdown(
                "מוסך", options, garageId,
                labelOf = { gid -> when (gid) { null -> "לא נבחר"; -1L -> "+ מוסך חדש"; else -> snap.garage(gid)?.name ?: "" } },
                onSelect = { if (it == -1L) addGarage = true else garageId = it },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("חלקים ₪", parts, { parts = it }, Modifier.weight(1f), decimal = true)
            NumberField("עבודה ₪", labor, { labor = it }, Modifier.weight(1f), decimal = true)
        }
        val total = (parts.toDoubleOrNullClean() ?: 0.0) + (labor.toDoubleOrNullClean() ?: 0.0)
        if (total > 0) Text("סה\"כ ${money(total)}")
        TextInput("הערות", notes, { notes = it }, multiline = true)
        AttachmentsEditor(files) { files = it }
    }

    if (addGarage) {
        GarageDialog(null, onSave = { g -> scope.launch { garageId = vm.repo.saveGarage(g) } }, onDismiss = { addGarage = false })
    }
}

// ======================= Fuel =======================

@Composable
fun FuelEditScreen(vm: AppViewModel, nav: NavController, vehicleId: Long, id: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val existing = snap.fuel.find { it.id == id }
    val scope = rememberCoroutineScope()
    val currentKm = Calc.currentKm(snap.readingsOf(vehicleId).filter { !(it.source == ReadingSource.FUEL && it.refId == id) })

    var date by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.date) } ?: LocalDate.now()) }
    var kmText by remember { mutableStateOf(existing?.odometerKm?.toString() ?: "") }
    var liters by remember { mutableStateOf(existing?.liters.str()) }
    var cost by remember { mutableStateOf(existing?.totalCost.str()) }
    var full by remember { mutableStateOf(existing?.fullTank ?: true) }
    var confirmedLow by remember { mutableStateOf(false) }

    val kmValue = kmText.toIntOrNullClean()
    val checkKm = if (date >= LocalDate.now()) kmValue else null
    val kmOk = checkKm == null || checkKm >= currentKm || confirmedLow
    FormScaffold(
        title = if (existing == null) "תדלוק" else "עריכת תדלוק",
        nav = nav,
        saveEnabled = kmValue != null && liters.toDoubleOrNullClean() != null && kmOk,
        onSave = {
            val f = FuelEntry(
                id = existing?.id ?: 0, vehicleId = vehicleId, date = date.toEpochDay(), odometerKm = kmValue ?: 0,
                liters = liters.toDoubleOrNullClean() ?: 0.0, totalCost = cost.toDoubleOrNullClean() ?: 0.0, fullTank = full,
            )
            scope.launch { vm.repo.saveFuel(f); nav.popBackStack() }
        },
        onDelete = if (existing == null) null else { { scope.launch { vm.repo.deleteFuel(existing.id) }; nav.popBackStack() } },
    ) {
        DateField("תאריך", date, { date = it })
        NumberField("$KM במד", kmText, { kmText = it }, supporting = "נוכחי: $currentKm")
        lowerKmGuard(checkKm, currentKm, confirmedLow) { confirmedLow = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("ליטרים", liters, { liters = it }, Modifier.weight(1f), decimal = true)
            NumberField("סכום ₪", cost, { cost = it }, Modifier.weight(1f), decimal = true)
        }
        SwitchRow("מילאתי מיכל מלא", full) { full = it }
        Text("הצריכה מחושבת רק בין שני תדלוקים של מיכל מלא", style = MaterialTheme.typography.bodySmall)
    }
}

// ======================= Documents =======================

@Composable
fun DocumentEditScreen(vm: AppViewModel, nav: NavController, vehicleId: Long, id: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val existing = snap.documents.find { it.id == id }
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(existing?.type ?: DocType.TEST) }
    var expiry by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.expiryDate) } ?: LocalDate.now().plusYears(1)) }
    var cost by remember { mutableStateOf(existing?.cost.str()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var files by remember { mutableStateOf<List<Attachment>>(emptyList()) }

    LaunchedEffect(Unit) { if (existing != null) files = vm.repo.attachmentsOf(OwnerType.DOCUMENT, existing.id) }

    FormScaffold(
        title = if (existing == null) "מסמך חדש" else "עריכת מסמך",
        nav = nav,
        saveEnabled = true,
        onSave = {
            val d = VehicleDocument(
                id = existing?.id ?: 0, vehicleId = vehicleId, type = type, expiryDate = expiry.toEpochDay(),
                cost = cost.toDoubleOrNullClean(), notes = notes.trim(),
            )
            scope.launch { vm.repo.saveDocument(d, files); nav.popBackStack() }
        },
        onDelete = if (existing == null) null else { { scope.launch { vm.repo.deleteDocument(existing.id) }; nav.popBackStack() } },
    ) {
        Dropdown("סוג", DocType.entries, type, { it.label() }, { type = it })
        DateField("בתוקף עד", expiry, { expiry = it })
        NumberField("עלות ₪ (לא חובה)", cost, { cost = it }, decimal = true)
        TextInput("הערות (חברת ביטוח, מספר פוליסה...)", notes, { notes = it }, multiline = true)
        AttachmentsEditor(files) { files = it }
        Text("כשמחדשים: פשוט לעדכן את התאריך, או להוסיף מסמך חדש ולמחוק את הישן.", style = MaterialTheme.typography.bodySmall)
    }
}

// ======================= Garage dialog (shared) =======================

@Composable
fun GarageDialog(existing: Garage?, onSave: (Garage) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "מוסך חדש" else "עריכת מוסך") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("שם", name, { name = it })
                NumberField("טלפון", phone, { phone = it })
                TextInput("כתובת", address, { address = it })
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text("מחק מוסך", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(Garage(existing?.id ?: 0, name.trim(), phone.trim(), address.trim()))
                onDismiss()
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}
