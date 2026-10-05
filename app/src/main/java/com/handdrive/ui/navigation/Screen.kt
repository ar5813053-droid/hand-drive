package com.handdrive.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Controller : Screen("controller")
    data object Profiles : Screen("profiles")
    data object Calibration : Screen("calibration")
    data object Settings : Screen("settings")
    data object About : Screen("about")
}
