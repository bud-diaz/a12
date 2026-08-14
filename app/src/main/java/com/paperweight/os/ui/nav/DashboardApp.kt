package com.paperweight.os.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paperweight.os.ui.dashboard.broadcast.BroadcastScreen
import com.paperweight.os.ui.dashboard.overview.OverviewScreen
import kotlinx.coroutines.launch

// Studio's AppShell.tsx has a fixed 248px sidebar with a mobile-breakpoint
// drawer fallback — this phone-sized app always uses that drawer form,
// opened from the top bar's menu icon.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DashboardDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = DashboardDestination.entries.firstOrNull { it.route == currentRoute }?.label ?: "",
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { innerPadding ->
            DashboardNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun DashboardNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = DashboardDestination.Overview.route,
        modifier = modifier,
    ) {
        composable(DashboardDestination.Overview.route) { OverviewScreen() }
        composable(DashboardDestination.Broadcast.route) { BroadcastScreen() }
        composable(DashboardDestination.Schedule.route) { ComingSoonScreen(DashboardDestination.Schedule.label) }
        composable(DashboardDestination.Vault.route) { ComingSoonScreen(DashboardDestination.Vault.label) }
        composable(DashboardDestination.Station.route) { ComingSoonScreen(DashboardDestination.Station.label) }
        composable(DashboardDestination.Audience.route) { ComingSoonScreen(DashboardDestination.Audience.label) }
        composable(DashboardDestination.Analytics.route) { ComingSoonScreen(DashboardDestination.Analytics.label) }
        composable(DashboardDestination.Earnings.route) { ComingSoonScreen(DashboardDestination.Earnings.label) }
        composable(DashboardDestination.Settings.route) { ComingSoonScreen(DashboardDestination.Settings.label) }
    }
}
