package com.handdrive.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.handdrive.ui.about.AboutScreen
import com.handdrive.ui.calibration.CalibrationScreen
import com.handdrive.ui.controller.ControllerScreen
import com.handdrive.ui.home.HomeScreen
import com.handdrive.ui.profiles.ProfilesScreen
import com.handdrive.ui.settings.SettingsScreen

@Composable
fun HandDriveNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartController = { navController.navigate(Screen.Controller.route) },
                onProfiles = { navController.navigate(Screen.Profiles.route) },
                onCalibration = { navController.navigate(Screen.Calibration.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onAbout = { navController.navigate(Screen.About.route) }
            )
        }
        composable(Screen.Controller.route) {
            ControllerScreen(
                onBack = { navController.popBackStack() },
                onCalibrate = { navController.navigate(Screen.Calibration.route) }
            )
        }
        composable(Screen.Profiles.route) {
            ProfilesScreen(
                onBack = { navController.popBackStack() },
                onCalibrate = { navController.navigate(Screen.Calibration.route) }
            )
        }
        composable(Screen.Calibration.route) {
            CalibrationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
