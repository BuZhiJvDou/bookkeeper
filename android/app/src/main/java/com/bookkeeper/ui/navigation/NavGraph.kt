package com.bookkeeper.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bookkeeper.ui.home.HomeScreen
import com.bookkeeper.ui.transaction.AddTransactionScreen
import com.bookkeeper.ui.transaction.TransactionListScreen
import com.bookkeeper.ui.transaction.TransferScreen
import com.bookkeeper.ui.statistics.StatisticsScreen
import com.bookkeeper.ui.settings.SettingsScreen
import com.bookkeeper.ui.category.CategoryScreen
import com.bookkeeper.ui.account.AccountScreen
import com.bookkeeper.ui.budget.BudgetScreen
import com.bookkeeper.ui.recurring.RecurringScreen
import com.bookkeeper.ui.sync.SyncScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Statistics : Screen("statistics", "统计", Icons.Default.Star)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

sealed class SubScreen(val route: String) {
    data object AddTransaction : SubScreen("add_transaction")
    data object EditTransaction : SubScreen("edit_transaction/{id}") {
        fun createRoute(id: Long) = "edit_transaction/$id"
    }
    data object TransactionList : SubScreen("transaction_list")
    data object Categories : SubScreen("categories")
    data object Accounts : SubScreen("accounts")
    data object Budgets : SubScreen("budgets")
    data object Transfer : SubScreen("transfer")
    data object Recurring : SubScreen("recurring")
    data object Sync : SubScreen("sync")
}

val bottomNavItems = listOf(Screen.Home, Screen.Statistics, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookkeeperNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAddTransaction = { navController.navigate(SubScreen.AddTransaction.route) },
                    onViewAllTransactions = { navController.navigate(SubScreen.TransactionList.route) },
                    onTransfer = { navController.navigate(SubScreen.Transfer.route) }
                )
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToCategories = { navController.navigate(SubScreen.Categories.route) },
                    onNavigateToAccounts = { navController.navigate(SubScreen.Accounts.route) },
                    onNavigateToBudgets = { navController.navigate(SubScreen.Budgets.route) },
                    onNavigateToRecurring = { navController.navigate(SubScreen.Recurring.route) },
                    onNavigateToSync = { navController.navigate(SubScreen.Sync.route) }
                )
            }
            composable(SubScreen.AddTransaction.route) {
                AddTransactionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.TransactionList.route) {
                TransactionListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAddTransaction = { navController.navigate(SubScreen.AddTransaction.route) }
                )
            }
            composable(SubScreen.Categories.route) {
                CategoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.Accounts.route) {
                AccountScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.Budgets.route) {
                BudgetScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.Transfer.route) {
                TransferScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.Recurring.route) {
                RecurringScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SubScreen.Sync.route) {
                SyncScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
