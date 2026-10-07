package com.wilhelmbeiche.roommemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wilhelmbeiche.roommemory.ui.AddItemScreen
import com.wilhelmbeiche.roommemory.ui.HomeScreen
import com.wilhelmbeiche.roommemory.ui.ItemViewModel
import com.wilhelmbeiche.roommemory.ui.ScanScreen
import com.wilhelmbeiche.roommemory.ui.theme.RoomMemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val viewModel: ItemViewModel = viewModel()
            // Guard against popping the start screen, e.g. when "save" completes after a back press.
            val navigateBack: () -> Unit = {
                if (navController.previousBackStackEntry != null) navController.popBackStack()
            }

            RoomMemoryTheme {
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToScan = { navController.navigate("scan") },
                            onNavigateToEditItem = { id -> navController.navigate("edit_item/$id") }
                        )
                    }
                    composable("scan") {
                        ScanScreen(
                            onNavigateBack = navigateBack,
                            // Replace the scan screen, so "back" from manual entry returns home.
                            onEnterManually = {
                                navController.navigate("add_item") { popUpTo("scan") { inclusive = true } }
                            }
                        )
                    }
                    composable("add_item") {
                        AddItemScreen(
                            viewModel = viewModel,
                            itemId = null,
                            onNavigateBack = navigateBack
                        )
                    }
                    composable(
                        "edit_item/{itemId}",
                        arguments = listOf(navArgument("itemId") { type = NavType.LongType })
                    ) { entry ->
                        AddItemScreen(
                            viewModel = viewModel,
                            itemId = entry.arguments?.getLong("itemId"),
                            onNavigateBack = navigateBack
                        )
                    }
                }
            }
        }
    }
}
