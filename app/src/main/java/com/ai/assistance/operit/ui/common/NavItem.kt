package com.ai.assistance.operit.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Token
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R

// 应用导航项
sealed class NavItem(val route: String, val titleResId: Int, val icon: ImageVector) {
        object AiChat : NavItem("ai_chat", R.string.nav_ai_chat, Icons.Default.Email)
        object ShizukuCommands :
                NavItem("shizuku_commands", R.string.shizuku_commands, Icons.Default.Build)
        object Settings : NavItem("settings", R.string.nav_settings, Icons.Default.Settings)
        object ToolPermissions :
                NavItem("tool_permissions", R.string.tool_permissions, Icons.Default.Security)
        object UserPreferencesGuide :
                NavItem(
                        "user_preferences_guide",
                        R.string.user_preferences_guide,
                        Icons.Default.Person
                )
        object UserPreferencesSettings :
                NavItem(
                        "user_preferences_settings",
                        R.string.user_preferences_settings,
                        Icons.Default.Face
                )
        object ChatHistorySettings :
                NavItem(
                        "chat_history_settings",
                        R.string.chat_history_settings,
                        Icons.Default.History
                )
        object Packages : NavItem("packages", R.string.nav_packages, Icons.Default.Extension)
        object ProblemLibrary :
                NavItem("problem_library", R.string.nav_problem_library, Icons.Default.Storage)
        object Terminal : NavItem("terminal", R.string.terminal, Icons.Default.Terminal)
        object Toolbox : NavItem("toolbox", R.string.toolbox, Icons.Default.Apps)
        object About : NavItem("about", R.string.nav_about, Icons.Default.Info)
        object Mcp : NavItem("mcp", R.string.mcp, Icons.Default.Cloud)
        object Agreement :
                NavItem("agreement", R.string.nav_item_agreement, Icons.Default.Description)
        object Help : NavItem("help", R.string.nav_help, Icons.Default.Help)
        object TokenConfig : NavItem("token_config", R.string.token_config, Icons.Default.Token)
        object Agent : NavItem("agent", R.string.nav_agent, Icons.Default.Person)
}
