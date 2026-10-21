package com.matan.garage.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.matan.garage.data.Media
import com.matan.garage.data.Vehicle
import com.matan.garage.logic.Calc
import com.matan.garage.logic.Status
import com.matan.garage.logic.Thresholds
import com.matan.garage.ui.AppViewModel
import com.matan.garage.ui.Green
import com.matan.garage.ui.Loading
import com.matan.garage.ui.Orange
import com.matan.garage.ui.Red
import com.matan.garage.ui.Routes
import com.matan.garage.ui.UpdateKmDialog
import com.matan.garage.ui.color
import com.matan.garage.ui.emoji
import com.matan.garage.ui.fmtDay
import com.matan.garage.ui.km
import com.matan.garage.ui.label
import com.matan.garage.ui.remainingText
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val snap = vm.snapshot.collectAsStateWithLifecycle().value
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var kmFor by remember { mutableStateOf<Vehicle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("המוסך שלי") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.GARAGES) }) { Icon(Icons.Default.Build, "מוסכים") }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "הגדרות") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.vehicleEdit(0)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("כלי רכב") },
            )
        },
    ) { pad ->
        if (snap == null) {
            Box(Modifier.padding(pad)) { Loading() }
            return@Scaffold
        }
        val today = LocalDate.now()
        val th = Thresholds(settings.soonKm, settings.soonDays)
        val vehicles = snap.vehicles.filter { !it.archived }
        val activeIds = vehicles.map { it.id }.toSet()
        val expiring = snap.documents
            .filter { it.vehicleId in activeIds && it.expiryDate - today.toEpochDay() <= settings.docLeadDays }
            .sortedBy { it.expiryDate }

        if (vehicles.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🏍️ 🚗", fontSize = 48.sp)
                Text("עוד לא הוספת כלי רכב", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { nav.navigate(Routes.vehicleEdit(0)) }, Modifier.padding(top = 16.dp)) {
                    Text("הוסף כלי רכב")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (expiring.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("ניירת שפגה בקרוב", fontWeight = FontWeight.Bold)
                            expiring.forEach { d ->
                                val left = d.expiryDate - today.toEpochDay()
                                val v = snap.vehicle(d.vehicleId)
                                val whenText = if (left < 0) "פג לפני ${-left} ימים" else if (left == 0L) "פג היום" else "בעוד $left ימים"
                                Text("${v?.name}: ${d.type.label()} · ${fmtDay(d.expiryDate)} ($whenText)")
                            }
                        }
                    }
                }
            }
            items(vehicles, key = { it.id }) { v ->
                val states = Calc.vehicleItemStates(snap, v.id, today, th)
                val overdue = states.count { it.status == Status.OVERDUE }
                val soon = states.count { it.status == Status.DUE_SOON }
                val current = Calc.currentKm(snap.readingsOf(v.id))
                Card(onClick = { nav.navigate(Routes.vehicle(v.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VehicleAvatar(v, 56)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(v.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(v.make, v.model).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodySmall)
                                Text(km(current), style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { kmFor = v }) { Text("עדכון ק\"מ") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when {
                                overdue > 0 || soon > 0 -> {
                                    if (overdue > 0) Text("● $overdue באיחור", color = Red)
                                    if (soon > 0) Text("● $soon מתקרבים", color = Orange)
                                }
                                states.isNotEmpty() -> Text("● הכל תקין", color = Green)
                                else -> Text("אין פריטי תחזוקה", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        states.firstOrNull()?.takeIf { it.status != Status.OK }?.let {
                            Text("${it.item.name}: ${it.remainingText()}", color = it.status.color(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        kmFor?.let { v ->
            UpdateKmDialog(
                current = Calc.currentKm(snap.readingsOf(v.id)),
                onSave = { value -> scope.launch { vm.repo.addReading(v.id, value) } },
                onDismiss = { kmFor = null },
            )
        }
    }
}

@Composable
fun VehicleAvatar(v: Vehicle, sizeDp: Int) {
    val context = LocalContext.current
    val bmp = remember(v.photoPath) { v.photoPath?.let { Media.loadBitmap(context, it, sizeDp * 3) } }
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(v.type.emoji(), fontSize = (sizeDp / 2).sp)
        }
    }
}
