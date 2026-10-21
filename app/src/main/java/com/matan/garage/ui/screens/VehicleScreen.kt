package com.matan.garage.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.garage.data.PerformedBy
import com.matan.garage.data.ServiceType
import com.matan.garage.data.Vehicle
import com.matan.garage.logic.Calc
import com.matan.garage.logic.CostCategory
import com.matan.garage.logic.ItemState
import com.matan.garage.logic.Snapshot
import com.matan.garage.logic.Thresholds
import com.matan.garage.ui.AppViewModel
import com.matan.garage.ui.ConfirmDialog
import com.matan.garage.ui.Dropdown
import com.matan.garage.ui.Green
import com.matan.garage.ui.Loading
import com.matan.garage.ui.Orange
import com.matan.garage.ui.Red
import com.matan.garage.ui.Routes
import com.matan.garage.ui.UpdateKmDialog
import com.matan.garage.ui.color
import com.matan.garage.ui.fmtDay
import com.matan.garage.ui.km
import com.matan.garage.ui.label
import com.matan.garage.ui.money
import com.matan.garage.ui.remainingText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Tab(val title: String) { MAINT("תחזוקה"), HISTORY("היסטוריה"), FUEL("דלק"), DOCS("ניירת"), COSTS("עלויות") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(vm: AppViewModel, nav: NavController, vehicleId: Long) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var updateKm by remember { mutableStateOf(false) }

    val v = snap?.vehicle(vehicleId)
    if (snap == null || v == null) {
        Loading(); return
    }
    val tabs = Tab.entries.filter { it != Tab.FUEL || v.trackFuel }
    val tab = tabs[tabIndex.coerceIn(0, tabs.lastIndex)]
    val today = LocalDate.now()
    val current = Calc.currentKm(snap.readingsOf(v.id))

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(v.name) },
                    navigationIcon = { BackButton(nav) },
                    actions = {
                        IconButton(onClick = { nav.navigate(Routes.vehicleEdit(v.id)) }) { Icon(Icons.Default.Edit, "עריכה") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "עוד") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (v.archived) "החזר מהארכיון" else "העבר לארכיון (נמכר)") },
                                onClick = {
                                    menu = false
                                    scope.launch { vm.repo.updateVehicle(v.copy(archived = !v.archived)) }
                                    if (!v.archived) nav.popBackStack()
                                },
                            )
                            DropdownMenuItem(text = { Text("מחק כלי רכב", color = Red) }, onClick = { menu = false; askDelete = true })
                        }
                    },
                )
                ScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = t == tab, onClick = { tabIndex = i }, text = { Text(t.title) }) }
                }
            }
        },
        floatingActionButton = {
            val (label, route) = when (tab) {
                Tab.MAINT, Tab.HISTORY -> "טיפול חדש" to Routes.service(v.id, 0)
                Tab.FUEL -> "תדלוק" to Routes.fuel(v.id, 0)
                Tab.DOCS -> "מסמך" to Routes.document(v.id, 0)
                Tab.COSTS -> null to null
            }
            if (label != null && route != null) {
                ExtendedFloatingActionButton(onClick = { nav.navigate(route) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(label) })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.MAINT -> MaintenanceTab(snap, v, current, Thresholds(settings.soonKm, settings.soonDays), nav) { updateKm = true }
                Tab.HISTORY -> HistoryTab(snap, v, nav)
                Tab.FUEL -> FuelTab(snap, v, nav)
                Tab.DOCS -> DocsTab(snap, v, today, nav)
                Tab.COSTS -> CostsTab(snap, v, today)
            }
        }
    }

    if (updateKm) {
        UpdateKmDialog(current, onSave = { value -> scope.launch { vm.repo.addReading(v.id, value) } }, onDismiss = { updateKm = false })
    }
    if (askDelete) {
        ConfirmDialog(
            "למחוק את ${v.name} עם כל ההיסטוריה? (אפשר במקום זה להעביר לארכיון)",
            onConfirm = { scope.launch { vm.repo.deleteVehicle(v) }; nav.popBackStack() },
            onDismiss = { askDelete = false },
        )
    }
}

private val listPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp)

@Composable
private fun MaintenanceTab(snap: Snapshot, v: Vehicle, current: Int, th: Thresholds, nav: NavController, onUpdateKm: () -> Unit) {
    val states = Calc.vehicleItemStates(snap, v.id, LocalDate.now(), th)
    val inactive = snap.itemsOf(v.id).filter { !it.active }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(km(current), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onUpdateKm) { Text("עדכון ק\"מ") }
            }
        }
        items(states, key = { it.item.id }) { st -> ItemCard(st) { nav.navigate(Routes.item(st.item.id)) } }
        if (inactive.isNotEmpty()) {
            item { Text("לא פעילים", style = MaterialTheme.typography.titleSmall) }
            items(inactive, key = { it.id }) { i ->
                Card(onClick = { nav.navigate(Routes.item(i.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(i.name, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            TextButton(onClick = { nav.navigate(Routes.itemEdit(v.id, 0)) }) { Text("+ הוסף פריט תחזוקה") }
        }
    }
}

@Composable
fun ItemCard(st: ItemState, onClick: () -> Unit) {
    val color = st.status.color()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row {
            Box(Modifier.width(6.dp).height(84.dp).background(color))
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(st.item.name, fontWeight = FontWeight.Bold)
                st.kmProgress?.let { p -> LinearProgressIndicator(progress = { p }, color = color, modifier = Modifier.fillMaxWidth()) }
                st.timeProgress?.let { p -> LinearProgressIndicator(progress = { p }, color = color.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) }
                Text(st.remainingText(), style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

@Composable
private fun HistoryTab(snap: Snapshot, v: Vehicle, nav: NavController) {
    var filterItem by rememberSaveable { mutableLongStateOf(0L) }
    val items = snap.itemsOf(v.id)
    val records = snap.recordsOf(v.id).filter { filterItem == 0L || filterItem in snap.itemIdsOf(it.id) }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Dropdown(
                "סינון לפי פריט", listOf(0L) + items.map { it.id }, filterItem,
                labelOf = { id -> if (id == 0L) "הכל" else snap.item(id)?.name ?: "" },
                onSelect = { filterItem = it },
            )
        }
        if (records.isEmpty()) item { Text("אין טיפולים עדיין", Modifier.padding(16.dp)) }
        items(records, key = { it.id }) { r ->
            val names = snap.itemIdsOf(r.id).mapNotNull { snap.item(it)?.name }
            val by = if (r.performedBy == PerformedBy.SELF) "עצמאי" else snap.garage(r.garageId)?.name ?: "מוסך"
            Card(onClick = { nav.navigate(Routes.service(v.id, r.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(fmtDay(r.date), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(km(r.odometerKm))
                    }
                    Text((if (r.type == ServiceType.REPAIR) "תיקון" else "טיפול") + " · " + by, style = MaterialTheme.typography.bodySmall)
                    if (names.isNotEmpty()) Text(names.joinToString(", "))
                    val total = r.partsCost + r.laborCost
                    if (total > 0) Text(money(total), style = MaterialTheme.typography.bodySmall)
                    if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun FuelTab(snap: Snapshot, v: Vehicle, nav: NavController) {
    val entries = snap.fuelOf(v.id)
    val stats = Calc.fuelStats(entries).reversed()
    val avg = Calc.averageKmPerLiter(entries)
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                if (avg != null) "צריכה ממוצעת: ${"%.1f".format(avg)} ק\"מ/ליטר" else "צריכה תחושב אחרי שני תדלוקים של מיכל מלא",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(stats, key = { it.entry.id }) { s ->
            val e = s.entry
            Card(onClick = { nav.navigate(Routes.fuel(v.id, e.id)) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(fmtDay(e.date), fontWeight = FontWeight.Bold)
                        Text("${km(e.odometerKm)} · ${"%.2f".format(e.liters)} ליטר" + if (!e.fullTank) " (חלקי)" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(e.totalCost))
                        s.kmPerLiter?.let { Text("${"%.1f".format(it)} ק\"מ/ל'", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocsTab(snap: Snapshot, v: Vehicle, today: LocalDate, nav: NavController) {
    val docs = snap.documentsOf(v.id)
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (docs.isEmpty()) item { Text("הוסף טסט, ביטוחים ורישיון רכב כדי לקבל תזכורת לפני שהם פגים", Modifier.padding(16.dp)) }
        items(docs, key = { it.id }) { d ->
            val left = d.expiryDate - today.toEpochDay()
            val color = when {
                left <= 0 -> Red
                left <= 30 -> Orange
                else -> Green
            }
            Card(onClick = { nav.navigate(Routes.document(v.id, d.id)) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.type.label(), fontWeight = FontWeight.Bold)
                        Text("בתוקף עד ${fmtDay(d.expiryDate)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (left < 0) "פג" else if (left == 0L) "היום" else "$left ימים", color = color)
                }
            }
        }
    }
}

@Composable
private fun CostsTab(snap: Snapshot, v: Vehicle, today: LocalDate) {
    val entries = Calc.costEntries(snap, v.id)
    val total = entries.sumOf { it.amount }
    val thisYear = entries.filter { it.date.year == today.year }.sumOf { it.amount }
    val distance = Calc.kmTracked(snap.readingsOf(v.id))
    val monthly = Calc.monthlyTotals(entries, today)
    val years = entries.groupBy { it.date.year }.mapValues { e -> e.value.sumOf { it.amount } }.toSortedMap(reverseOrder())

    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("סה\"כ: ${money(total)}", style = MaterialTheme.typography.titleLarge)
                    Text("השנה: ${money(thisYear)}")
                    if (distance > 0) Text("עלות לק\"מ: ${money(total / distance)} (על ${km(distance)})")
                }
            }
        }
        item {
            Text("12 חודשים אחרונים", style = MaterialTheme.typography.titleSmall)
            MonthlyChart(monthly)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("לפי קטגוריה", fontWeight = FontWeight.Bold)
                    CostCategory.entries.forEach { c ->
                        val sum = entries.filter { it.category == c }.sumOf { it.amount }
                        Row { Text(c.label(), Modifier.weight(1f)); Text(money(sum)) }
                    }
                }
            }
        }
        if (years.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("לפי שנה", fontWeight = FontWeight.Bold)
                        years.forEach { (y, sum) ->
                            Row { Text("$y", Modifier.weight(1f)); Text(money(sum)) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Simple bar chart, oldest month on the left. */
@Composable
private fun MonthlyChart(data: List<Pair<java.time.YearMonth, Double>>) {
    val max = data.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val monthFmt = DateTimeFormatter.ofPattern("MM")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
                val slot = size.width / data.size
                val barW = slot * 0.6f
                data.forEachIndexed { i, (_, value) ->
                    val h = (value / max).toFloat() * size.height
                    drawRect(
                        color = barColor,
                        topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                        size = Size(barW, h),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                data.forEach { (ym, _) ->
                    Text(
                        ym.format(monthFmt), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
