package com.bookkeeper

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.bookkeeper.data.repository.AccountRepository
import com.bookkeeper.data.repository.CategoryRepository
import com.bookkeeper.data.repository.RecurringRepository
import com.bookkeeper.ui.navigation.BookkeeperNavHost
import com.bookkeeper.ui.theme.BackgroundLayer
import com.bookkeeper.ui.theme.BookkeeperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var recurringRepository: RecurringRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先渲染 UI，再后台初始化，避免启动阶段阻塞/闪退
        setContent {
            BookkeeperTheme {
                // BackgroundLayer 自带：背景色 + 背景图 + 蒙版（控制可见度）
                Box(modifier = Modifier.fillMaxSize()) {
                    BackgroundLayer()
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent  // 完全透明，让背景图直接透出
                    ) {
                        BookkeeperNavHost()
                    }
                }
            }
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    categoryRepository.initDefaultCategories()
                    accountRepository.initDefaultAccounts()
                    recurringRepository.processDueRules()
                } catch (e: Exception) {
                    Log.e("MainActivity", "background init failed", e)
                }
            }
        }
    }
}
