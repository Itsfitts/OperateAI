package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Content for the expanded navigation drawer */
@Composable
fun DrawerContent(
        navItems: List<NavItem>,
        currentScreen: Screen,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        networkType: String,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        onScreenSelected: (Screen, NavItem) -> Unit
) {
    // 添加滚动功能的Column
    Column(
            modifier =
                    Modifier.fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(end = 8.dp) // Add some end padding
    ) {
        // 抽屉标题
        Spacer(modifier = Modifier.height(24.dp))
        Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        // 网络状态显示
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Icon(
                    imageVector =
                            if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "网络状态",
                    tint =
                            if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = networkType,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                            if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // 分组导航菜单

        // AI功能组
        NavigationDrawerItemHeader("AI功能")

        // AI聊天
        CompactNavigationDrawerItem(
                icon = NavItem.AiChat.icon,
                label = stringResource(id = NavItem.AiChat.titleResId),
                selected = currentScreen is Screen.AiChat,
                onClick = {
                    onScreenSelected(Screen.AiChat, NavItem.AiChat)
                    scope.launch { drawerState.close() }
                }
        )

        // Agent语音
        CompactNavigationDrawerItem(
            icon = NavItem.Agent.icon,
            label = stringResource(id = NavItem.Agent.titleResId),
            selected = currentScreen is Screen.Agent,
            onClick = {
                onScreenSelected(Screen.Agent, NavItem.Agent)
                scope.launch { drawerState.close() }
            }
        )

        // 问题库
        CompactNavigationDrawerItem(
                icon = NavItem.ProblemLibrary.icon,
                label = stringResource(id = NavItem.ProblemLibrary.titleResId),
                selected = currentScreen is Screen.ProblemLibrary,
                onClick = {
                    onScreenSelected(Screen.ProblemLibrary, NavItem.ProblemLibrary)
                    scope.launch { drawerState.close() }
                }
        )

        // 包管理
        CompactNavigationDrawerItem(
                icon = NavItem.Packages.icon,
                label = stringResource(id = NavItem.Packages.titleResId),
                selected = currentScreen is Screen.Packages,
                onClick = {
                    onScreenSelected(Screen.Packages, NavItem.Packages)
                    scope.launch { drawerState.close() }
                }
        )

        // 获取密钥
        CompactNavigationDrawerItem(
                icon = NavItem.TokenConfig.icon,
                label = stringResource(id = NavItem.TokenConfig.titleResId),
                selected = currentScreen is Screen.TokenConfig,
                onClick = {
                    onScreenSelected(Screen.TokenConfig, NavItem.TokenConfig)
                    scope.launch { drawerState.close() }
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 工具组
        NavigationDrawerItemHeader("工具")

        // 工具箱
        CompactNavigationDrawerItem(
                icon = NavItem.Toolbox.icon,
                label = stringResource(id = NavItem.Toolbox.titleResId),
                selected = currentScreen is Screen.Toolbox,
                onClick = {
                    onScreenSelected(Screen.Toolbox, NavItem.Toolbox)
                    scope.launch { drawerState.close() }
                }
        )

        // Shizuku命令
        CompactNavigationDrawerItem(
                icon = NavItem.ShizukuCommands.icon,
                label = stringResource(id = NavItem.ShizukuCommands.titleResId),
                selected = currentScreen is Screen.ShizukuCommands,
                onClick = {
                    onScreenSelected(Screen.ShizukuCommands, NavItem.ShizukuCommands)
                    scope.launch { drawerState.close() }
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 系统组
        NavigationDrawerItemHeader("系统")

        // 设置
        CompactNavigationDrawerItem(
                icon = NavItem.Settings.icon,
                label = stringResource(id = NavItem.Settings.titleResId),
                selected = currentScreen is Screen.Settings,
                onClick = {
                    onScreenSelected(Screen.Settings, NavItem.Settings)
                    scope.launch { drawerState.close() }
                }
        )

        // 帮助中心
        CompactNavigationDrawerItem(
                icon = NavItem.Help.icon,
                label = stringResource(id = NavItem.Help.titleResId),
                selected = currentScreen is Screen.Help,
                onClick = {
                    onScreenSelected(Screen.Help, NavItem.Help)
                    scope.launch { drawerState.close() }
                }
        )

        // 关于
        CompactNavigationDrawerItem(
                icon = NavItem.About.icon,
                label = stringResource(id = NavItem.About.titleResId),
                selected = currentScreen is Screen.About,
                onClick = {
                    onScreenSelected(Screen.About, NavItem.About)
                    scope.launch { drawerState.close() }
                }
        )

        // 为了在底部留出一些空间，避免最后一个选项贴底
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Content for the collapsed navigation drawer (for tablet mode) */
@Composable
fun CollapsedDrawerContent(
        navItems: List<NavItem>,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        onScreenSelected: (Screen, NavItem) -> Unit
) {
    // 折叠状态下只显示图标
    Column(
            modifier =
                    Modifier.fillMaxHeight()
                            .verticalScroll(rememberScrollState()) // 添加滚动支持
                            .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 抽屉标题 - 仅图标
        Spacer(modifier = Modifier.height(8.dp))

        // 网络状态图标 - 与其他图标保持一致
        IconButton(onClick = { /* 点击图标操作可选 */}) {
            Icon(
                    imageVector =
                            if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "网络状态",
                    tint =
                            if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp) // 与其他图标大小一致
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(modifier = Modifier.fillMaxWidth(0.6f))
        Spacer(modifier = Modifier.height(16.dp))

        // 图标列表 - 只显示图标按钮
        for (item in navItems) {
            IconButton(
                    onClick = {
                        val screen =
                                when (item) {
                                    NavItem.AiChat -> Screen.AiChat
                                    NavItem.ProblemLibrary -> Screen.ProblemLibrary
                                    NavItem.Packages -> Screen.Packages
                                    NavItem.Toolbox -> Screen.Toolbox
                                    NavItem.ShizukuCommands -> Screen.ShizukuCommands
                                    NavItem.Settings -> Screen.Settings
                                    NavItem.Help -> Screen.Help
                                    NavItem.About -> Screen.About
                                    NavItem.TokenConfig -> Screen.TokenConfig
                                    NavItem.Agent -> Screen.Agent
                                    else -> Screen.AiChat
                                }
                        onScreenSelected(screen, item)
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(id = item.titleResId),
                        tint =
                                if (selectedItem == item) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                )
            }
        }

        // 底部留白，避免最后一项靠底
        Spacer(modifier = Modifier.height(16.dp))
    }
}
