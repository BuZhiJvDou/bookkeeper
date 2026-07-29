package com.bookkeeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.bookkeeper.data.repository.RecurringRepository
import com.bookkeeper.ui.navigation.BookkeeperNavHost
import com.bookkeeper.ui.theme.BookkeeperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var recurringRepository: RecurringRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动时处理到期的循环记账规则（自动补记错过的周期）
        lifecycleScope.launch {
            try {
                recurringRepository.processDueRules()
            } catch (e: Exception) {
                // 忽略处理失败，不阻塞启动
            }
        }

        enableEdgeToEdge()
        setContent {
            BookkeeperTheme {
                BookkeeperNavHost()
            }
        }
    }
}
