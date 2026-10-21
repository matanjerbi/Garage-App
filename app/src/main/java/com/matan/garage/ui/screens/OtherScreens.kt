package com.matan.garage.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.garage.data.Backup
import com.matan.garage.data.Garage
import com.matan.garage.data.PerformedBy
import com.matan.garage.logic.Calc
import com.matan.garage.logic.Thresholds
import com.matan.garage.notify.Reminders
import com.matan.garage.ui.AppViewModel
import com.matan.garage.ui.ConfirmDialog
import com.matan.garage.ui.KM
import com.matan.garage.ui.Loading
import com.matan.garage.ui.NumberField
import com.matan.garage.ui.Routes
import com.matan.garage.ui.SectionTitle
import com.matan.garage.ui.emoji
import com.matan.garage.ui.fmtDay
import com.matan.garage.ui.km
import com.matan.garage.ui.label
import com.matan.garage.ui.money
import com.matan.garage.ui.toIntOrNullClean
import com.matan.garage.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ======================= Maintenance item detail =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(vm: AppViewModel, nav: NavController, itemId: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val item = snap.item(itemId)
    if (item == null) {
        LaunchedEffect(Unit) { nav.popBackStack() } // deleted
        return
    }
    val state = Calc.vehicleItemStates(snap, item.vehicleId, LocalDate.now(), Thresholds(settings.soonKm, settings.soonDays))
        .find { it.item.id == itemId }
    val history = snap.recordsWithItem(itemId).sortedByDescending { it.date }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name) },
                navigationIcon = { BackButton(nav) },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.itemEdit(item.vehicleId, item.id)) }) { Icon(Icons.Default.Edit, "עריכה") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.service(item.vehicleId, 0, item.id)) },
                icon = { Icon(Icons.Default.Check, null) },
                text = { Text("סמן כבוצע") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state != null) item { ItemCard(state) {} }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("קטגוריה: ${item.category.label()}")
                        val iv = listOfNotNull(item.intervalKm?.let { km(it) }, item.intervalMonths?.let { "$it חודשים" })
                        Text("מרווח: " + if (iv.isEmpty()) "לא מוגדר" else iv.joinToString(" או "))
                        state?.let { Text("בוצע לאחרונה: ${km(it.lastKm)} · ${fmtDay(it.lastDate.toEpochDay())}") }
                        if (item.notes.isNotBlank()) Text(item.notes)
                        if (!item.active) Text("הפריט לא פעיל", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { SectionTitle("היסטוריה") }
            if (history.isEmpty()) item { Text("עוד לא תועד טיפול לפריט הזה") }
            items(history, key = { it.id }) { r ->
                val by = if (r.performedBy == PerformedBy.SELF) "עצמאי" else snap.garage(r.garageId)?.name ?: "מוסך"
                Card(onClick = { nav.navigate(Routes.service(r.vehicleId, r.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(fmtDay(r.date), fontWeight = FontWeight.Bold)
                            Text("${km(r.odometerKm)} · $by", style = MaterialTheme.typography.bodySmall)
                        }
                        val total = r.partsCost + r.laborCost
                        if (total > 0) Text(money(total))
                    }
                }
            }
        }
    }
}

// ======================= Garages =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaragesScreen(vm: AppViewModel, nav: NavController) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Garage?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("מוסכים") }, navigationIcon = { BackButton(nav) }) },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "הוסף") } },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (snap.garages.isEmpty()) item { Text("עוד לא הוספת מוסכים") }
            items(snap.garages, key = { it.id }) { g ->
                val visits = snap.records.count { it.garageId == g.id }
                Card(onClick = { editing = g }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontWeight = FontWeight.Bold)
                            if (g.address.isNotBlank()) Text(g.address, style = MaterialTheme.typography.bodySmall)
                            Text("$visits טיפולים", style = MaterialTheme.typography.bodySmall)
                        }
                        if (g.phone.isNotBlank()) {
                            IconButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${g.phone}")))
                            }) { Icon(Icons.Default.Call, "חייג") }
                        }
                    }
                }
            }
        }
    }
    if (adding) GarageDialog(null, onSave = { scope.launch { vm.repo.saveGarage(it) } }, onDismiss = { adding = false })
    editing?.let { g ->
        GarageDialog(
            g,
            onSave = { scope.launch { vm.repo.saveGarage(it) } },
            onDismiss = { editing = null },
            onDelete = { scope.launch { vm.repo.deleteGarage(g.id) } },
        )
    }
}

// ======================= Settings =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value ?: return Loading()
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var soonKm by remember { mutableStateOf(s.soonKm.toString()) }
    var soonDays by remember { mutableStateOf(s.soonDays.toString()) }
    var docLead by remember { mutableStateOf(s.docLeadDays.toString()) }
    var odoDays by remember { mutableStateOf(s.odoReminderDays.toString()) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.export(context, vm.repo, it) } }.isSuccess
            }
            context.toast(if (ok) "הגיבוי נשמר" else "שמירת הגיבוי נכשלה")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    val changed = soonKm != s.soonKm.toString() || soonDays != s.soonDays.toString() ||
        docLead != s.docLeadDays.toString() || odoDays != s.odoReminderDays.toString()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הגדרות") },
                navigationIcon = { BackButton(nav) },
                actions = {
                    TextButton(enabled = changed, onClick = {
                        vm.settingsStore.update(
                            s.copy(
                                soonKm = soonKm.toIntOrNullClean() ?: s.soonKm,
                                soonDays = soonDays.toIntOrNullClean() ?: s.soonDays,
                                docLeadDays = docLead.toIntOrNullClean() ?: s.docLeadDays,
                                odoReminderDays = odoDays.toIntOrNullClean() ?: s.odoReminderDays,
                            )
                        )
                        context.toast("נשמר")
                    }) { Text("שמור") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("תזכורות")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("שעת בדיקה יומית: %02d:%02d".format(s.notifHour, s.notifMinute), Modifier.weight(1f))
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        val ns = s.copy(notifHour = h, notifMinute = m)
                        vm.settingsStore.update(ns)
                        Reminders.schedule(context, ns, replace = true)
                    }, s.notifHour, s.notifMinute, true).show()
                }) { Text("שנה") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("\"מתקרב\" - $KM לפני", soonKm, { soonKm = it }, Modifier.weight(1f))
                NumberField("\"מתקרב\" - ימים לפני", soonDays, { soonDays = it }, Modifier.weight(1f))
            }
            NumberField("תזכורת לניירת - ימים לפני שפג", docLead, { docLead = it })
            NumberField("תזכורת לעדכן $KM אחרי (ימים)", odoDays, { odoDays = it }, supporting = "0 = בלי תזכורת")
            OutlinedButton(onClick = { Reminders.runNow(context); context.toast("בודק עכשיו...") }) { Text("בדוק תזכורות עכשיו") }

            HorizontalDivider()
            SectionTitle("גיבוי")
            Text("הנתונים שמורים רק בטלפון. כדאי לגבות מדי פעם ולשמור את הקובץ ב-Drive או לשלוח לעצמך.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exporter.launch("garage-backup-${LocalDate.now()}.zip") }) { Text("ייצוא גיבוי") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("שחזור מגיבוי") }
            }

            val archived = snap.vehicles.filter { it.archived }
            if (archived.isNotEmpty()) {
                HorizontalDivider()
                SectionTitle("ארכיון")
                archived.forEach { v ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${v.type.emoji()} ${v.name}", Modifier.weight(1f))
                            TextButton(onClick = { nav.navigate(Routes.vehicle(v.id)) }) { Text("צפה") }
                            TextButton(onClick = { scope.launch { vm.repo.updateVehicle(v.copy(archived = false)) } }) { Text("שחזר") }
                            TextButton(onClick = { deleteId = v.id }) { Text("מחק", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            "השחזור יחליף את כל הנתונים הנוכחיים בנתונים מהגיבוי. להמשיך?",
            confirm = "שחזר",
            onConfirm = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.import(context, vm.repo, it) } }
                    }
                    context.toast(if (result.isSuccess) "השחזור הושלם" else "השחזור נכשל: ${result.exceptionOrNull()?.message}")
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
    deleteId?.let { id ->
        val v = snap.vehicle(id)
        if (v != null) {
            ConfirmDialog(
                "למחוק לצמיתות את ${v.name} עם כל ההיסטוריה?",
                onConfirm = { scope.launch { vm.repo.deleteVehicle(v) } },
                onDismiss = { deleteId = null },
            )
        }
    }
}
