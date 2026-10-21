package com.matan.garage.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.matan.garage.data.Attachment
import com.matan.garage.data.Category
import com.matan.garage.data.DocType
import com.matan.garage.data.Media
import com.matan.garage.data.OwnerType
import com.matan.garage.data.VehicleType
import com.matan.garage.logic.CostCategory
import com.matan.garage.logic.ItemState
import com.matan.garage.logic.Status
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------- theme ----------

@Composable
fun GarageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9CC4FF), secondary = Color(0xFFFFB74D))
        else -> lightColorScheme(primary = Color(0xFF1F3A5F), secondary = Color(0xFFE08A00))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val Red = Color(0xFFD32F2F)
val Orange = Color(0xFFF57C00)
val Green = Color(0xFF388E3C)

fun Status.color() = when (this) {
    Status.OVERDUE -> Red
    Status.DUE_SOON -> Orange
    Status.OK -> Green
}

// ---------- formatting ----------

const val KM = "ק\"מ"
private val numFmt = DecimalFormat("#,###")
private val moneyFmt = DecimalFormat("#,##0.##")
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun km(v: Int) = "${numFmt.format(v)} $KM"
fun money(v: Double) = "₪${moneyFmt.format(v)}"
fun fmt(d: LocalDate): String = d.format(dateFmt)
fun fmtDay(epochDay: Long): String = fmt(LocalDate.ofEpochDay(epochDay))

fun Category.label() = when (this) {
    Category.ENGINE -> "מנוע"
    Category.DRIVETRAIN -> "הנעה ושרשרת"
    Category.BRAKES -> "בלמים"
    Category.TIRES -> "צמיגים"
    Category.ELECTRICAL -> "חשמל"
    Category.FLUIDS -> "נוזלים"
    Category.OTHER -> "אחר"
}

fun DocType.label() = when (this) {
    DocType.TEST -> "טסט"
    DocType.COMPULSORY_INSURANCE -> "ביטוח חובה"
    DocType.COMPREHENSIVE_INSURANCE -> "ביטוח מקיף / צד ג'"
    DocType.VEHICLE_LICENSE -> "רישיון רכב"
    DocType.OTHER -> "אחר"
}

fun VehicleType.label() = if (this == VehicleType.CAR) "רכב" else "אופנוע"
fun VehicleType.emoji() = if (this == VehicleType.CAR) "🚗" else "🏍️"

fun CostCategory.label() = when (this) {
    CostCategory.MAINTENANCE -> "טיפולים"
    CostCategory.REPAIR -> "תיקונים"
    CostCategory.FUEL -> "דלק"
    CostCategory.DOCUMENTS -> "ניירת וביטוח"
}

/** e.g. "עוד 1,200 ק"מ · עוד 40 ימים · ~15/12/2026" */
fun ItemState.remainingText(): String {
    val parts = mutableListOf<String>()
    kmLeft?.let { parts += if (it > 0) "עוד ${km(it)}" else "באיחור של ${km(-it)}" }
    daysLeft?.let { parts += if (it > 0) "עוד $it ימים" else "באיחור של ${-it} ימים" }
    if (status != Status.OVERDUE) dueDate?.let { parts += "~${fmt(it)}" }
    return if (parts.isEmpty()) "ללא מרווח מוגדר" else parts.joinToString(" · ")
}

fun String.toIntOrNullClean(): Int? = filter { it.isDigit() }.toIntOrNull()
fun String.toDoubleOrNullClean(): Double? = replace(",", ".").trim().toDoubleOrNull()
fun Int?.str() = this?.toString() ?: ""
fun Double?.str() = this?.let { moneyFmt.format(it).replace(",", "") } ?: ""

// ---------- small components ----------

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, multiline: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = fmt(date), onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("ביטול") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = labelOf(selected), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(labelOf(it)) }, onClick = { onSelect(it); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach {
            FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(labelOf(it)) })
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ConfirmDialog(text: String, confirm: String = "מחק", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Warns when the entered km is lower than the vehicle's current odometer. Returns true if saving is allowed. */
@Composable
fun lowerKmGuard(entered: Int?, current: Int, confirmed: Boolean, onConfirmed: (Boolean) -> Unit): Boolean {
    if (entered == null || entered >= current) return true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = confirmed, onCheckedChange = onConfirmed)
        Text("הערך נמוך מהקריאה האחרונה (${km(current)}). לשמור בכל זאת?", color = Red)
    }
    return confirmed
}

@Composable
fun UpdateKmDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    val value = text.toIntOrNullClean()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עדכון $KM") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("נוכחי: ${km(current)}")
                NumberField("$KM נוכחי", text, { text = it })
                lowerKmGuard(value, current, confirmed) { confirmed = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null && (value >= current || confirmed),
                onClick = { value?.let(onSave); onDismiss() },
            ) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

// ---------- attachments ----------

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AttachmentsEditor(files: List<Attachment>, onChange: (List<Attachment>) -> Unit) {
    val context = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingPhoto ?: return@rememberLauncherForActivityResult
        if (ok) onChange(files + Attachment(ownerType = OwnerType.SERVICE_RECORD, ownerId = 0, fileName = name, mimeType = "image/jpeg"))
        else Media.file(context, name).delete()
        pendingPhoto = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Media.importUri(context, uri)?.let { onChange(files + it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("קבצים מצורפים", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val f = Media.newPhotoFile(context)
                pendingPhoto = f.name
                camera.launch(Media.uriFor(context, f))
            }) { Text("📷 צלם") }
            OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) { Text("📎 צרף קובץ") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEachIndexed { i, a ->
                AssistChip(
                    onClick = { Media.open(context, a.fileName, a.mimeType) },
                    label = { Text(if (a.mimeType == "application/pdf") "📄 PDF ${i + 1}" else "🖼️ תמונה ${i + 1}") },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close, "הסר",
                            Modifier.size(18.dp).clickable { onChange(files.filterIndexed { j, _ -> j != i }) },
                        )
                    },
                )
            }
        }
    }
}

fun Context.toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}
