package com.zivxio.gitscreen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zivxio.gitscreen.data.repository.ScreenshotRepository
import com.zivxio.gitscreen.ui.screens.AppDetailScreen
import com.zivxio.gitscreen.ui.screens.HomeScreen
import com.zivxio.gitscreen.ui.screens.VersionDetailScreen
import com.zivxio.gitscreen.ui.viewmodel.AppDetailViewModel
import com.zivxio.gitscreen.ui.viewmodel.HomeViewModel
import com.zivxio.gitscreen.ui.viewmodel.VersionDetailViewModel
import java.net.URLDecoder
import java.net.URLEncoder

private object Routes {
    const val HOME = "home"
    const val APP_DETAIL = "app/{appId}/{appName}"
    const val VERSION_DETAIL = "app/{appId}/version/{versionId}/{versionLabel}"

    fun appDetail(appId: Long, appName: String) = "app/$appId/${URLEncoder.encode(appName, "UTF-8")}"
    fun versionDetail(appId: Long, versionId: Long, versionLabel: String) =
        "app/$appId/version/$versionId/${URLEncoder.encode(versionLabel, "UTF-8")}"
}

@Composable
fun GitScreenNavHost(repository: ScreenshotRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
            HomeScreen(
                viewModel = vm,
                onAppClick = { appId, appName ->
                    navController.navigate(Routes.appDetail(appId, appName))
                }
            )
        }

        composable(
            route = Routes.APP_DETAIL,
            arguments = listOf(
                navArgument("appId") { type = NavType.LongType },
                navArgument("appName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getLong("appId") ?: 0L
            val appName = URLDecoder.decode(backStackEntry.arguments?.getString("appName") ?: "", "UTF-8")
            val vm: AppDetailViewModel = viewModel(
                factory = AppDetailViewModel.Factory(repository, appId, appName)
            )
            AppDetailScreen(
                viewModel = vm,
                onVersionClick = { versionId, versionLabel ->
                    navController.navigate(Routes.versionDetail(appId, versionId, versionLabel))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.VERSION_DETAIL,
            arguments = listOf(
                navArgument("appId") { type = NavType.LongType },
                navArgument("versionId") { type = NavType.LongType },
                navArgument("versionLabel") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val versionId = backStackEntry.arguments?.getLong("versionId") ?: 0L
            val versionLabel =
                URLDecoder.decode(backStackEntry.arguments?.getString("versionLabel") ?: "", "UTF-8")
            val vm: VersionDetailViewModel = viewModel(
                factory = VersionDetailViewModel.Factory(repository, versionId, versionLabel)
            )
            VersionDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
