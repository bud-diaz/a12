package com.paperweight.os.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

// Mirrors AppShell.tsx's navGroups, scoped to the nine views this app ports.
enum class DashboardDestination(val route: String, val label: String, val icon: ImageVector) {
    Overview("overview", "Overview", Icons.Outlined.Dashboard),
    Broadcast("broadcast", "Broadcast", Icons.Outlined.Radio),
    Schedule("schedule", "Schedule", Icons.Outlined.Schedule),
    Vault("vault", "Vault", Icons.Outlined.Lock),
    Station("station", "Station", Icons.Outlined.Public),
    Audience("audience", "Audience", Icons.Outlined.People),
    Analytics("analytics", "Analytics", Icons.Outlined.BarChart),
    Earnings("earnings", "Earnings", Icons.Outlined.Payments),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}
