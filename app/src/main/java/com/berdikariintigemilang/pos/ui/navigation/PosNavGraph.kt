package com.berdikariintigemilang.pos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.berdikariintigemilang.pos.ui.auth.LoginScreen
import com.berdikariintigemilang.pos.ui.main.AppViewModel
import com.berdikariintigemilang.pos.ui.main.MainScreen
import com.berdikariintigemilang.pos.ui.shift.ShiftCloseScreen
import com.berdikariintigemilang.pos.ui.shift.ShiftOpenScreen
import com.berdikariintigemilang.pos.ui.shift.ZReportScreen
import com.berdikariintigemilang.pos.ui.splash.SplashDestination
import com.berdikariintigemilang.pos.ui.splash.SplashScreen

@Composable
fun PosNavGraph(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = hiltViewModel()
) {
    // Auto-logout saat sesi berakhir (401).
    LaunchedEffect(Unit) {
        appViewModel.loggedOut.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(onNavigate = { dest ->
                val target = when (dest) {
                    SplashDestination.LOGIN -> Routes.LOGIN
                    SplashDestination.SHIFT_OPEN -> Routes.SHIFT_OPEN
                    SplashDestination.MAIN -> Routes.MAIN
                }
                navController.navigate(target) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.SHIFT_OPEN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.SHIFT_OPEN) {
            ShiftOpenScreen(onShiftOpen = {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.SHIFT_OPEN) { inclusive = true }
                }
            })
        }

        composable(Routes.MAIN) {
            MainScreen(
                onCloseShift = { shiftId -> navController.navigate(Routes.shiftClose(shiftId)) },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.SHIFT_CLOSE,
            arguments = listOf(navArgument("shiftId") { type = NavType.StringType })
        ) {
            ShiftCloseScreen(
                onBack = { navController.popBackStack() },
                onClosed = { shiftId ->
                    navController.navigate(Routes.zReport(shiftId)) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.Z_REPORT,
            arguments = listOf(navArgument("shiftId") { type = NavType.StringType })
        ) {
            ZReportScreen(onDone = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            })
        }
    }
}
