package com.matan.garage.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matan.garage.GarageApp
import com.matan.garage.logic.Snapshot
import com.matan.garage.ui.screens.DocumentEditScreen
import com.matan.garage.ui.screens.FuelEditScreen
import com.matan.garage.ui.screens.GaragesScreen
import com.matan.garage.ui.screens.HomeScreen
import com.matan.garage.ui.screens.ItemEditScreen
import com.matan.garage.ui.screens.ItemScreen
import com.matan.garage.ui.screens.ServiceEditScreen
import com.matan.garage.ui.screens.SettingsScreen
import com.matan.garage.ui.screens.VehicleEditScreen
import com.matan.garage.ui.screens.VehicleScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The one ViewModel: holds the whole database as a [Snapshot] that every screen derives its UI from. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val garageApp = app as GarageApp
    val repo = garageApp.repo
    val settingsStore = garageApp.settings
    val settings = settingsStore.flow
    val snapshot: StateFlow<Snapshot?> = repo.snapshot().stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

object Routes {
    const val HOME = "home"
    fun vehicle(id: Long) = "vehicle/$id"
    fun vehicleEdit(id: Long) = "vehicleEdit/$id"
    fun item(id: Long) = "item/$id"
    fun itemEdit(vehicleId: Long, id: Long) = "itemEdit/$vehicleId/$id"
    fun service(vehicleId: Long, id: Long, itemId: Long = 0) = "service/$vehicleId/$id?item=$itemId"
    fun fuel(vehicleId: Long, id: Long) = "fuel/$vehicleId/$id"
    fun document(vehicleId: Long, id: Long) = "document/$vehicleId/$id"
    const val GARAGES = "garages"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav(openVehicle: MutableStateFlow<Long?>) {
    val nav: NavHostController = rememberNavController()
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current

    // Ask for notification permission once (Android 13+)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Opened from a notification
    val target by openVehicle.collectAsState()
    LaunchedEffect(target) {
        target?.let {
            nav.navigate(Routes.vehicle(it)) { launchSingleTop = true }
            openVehicle.value = null
        }
    }

    val long = { name: String -> navArgument(name) { type = NavType.LongType } }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(vm, nav) }
        composable("vehicle/{id}", listOf(long("id"))) {
            VehicleScreen(vm, nav, it.arguments!!.getLong("id"))
        }
        composable("vehicleEdit/{id}", listOf(long("id"))) {
            VehicleEditScreen(vm, nav, it.arguments!!.getLong("id"))
        }
        composable("item/{id}", listOf(long("id"))) {
            ItemScreen(vm, nav, it.arguments!!.getLong("id"))
        }
        composable("itemEdit/{vid}/{id}", listOf(long("vid"), long("id"))) {
            ItemEditScreen(vm, nav, it.arguments!!.getLong("vid"), it.arguments!!.getLong("id"))
        }
        composable(
            "service/{vid}/{id}?item={item}",
            listOf(long("vid"), long("id"), navArgument("item") { type = NavType.LongType; defaultValue = 0L }),
        ) {
            val a = it.arguments!!
            ServiceEditScreen(vm, nav, a.getLong("vid"), a.getLong("id"), a.getLong("item"))
        }
        composable("fuel/{vid}/{id}", listOf(long("vid"), long("id"))) {
            FuelEditScreen(vm, nav, it.arguments!!.getLong("vid"), it.arguments!!.getLong("id"))
        }
        composable("document/{vid}/{id}", listOf(long("vid"), long("id"))) {
            DocumentEditScreen(vm, nav, it.arguments!!.getLong("vid"), it.arguments!!.getLong("id"))
        }
        composable(Routes.GARAGES) { GaragesScreen(vm, nav) }
        composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
    }
}
