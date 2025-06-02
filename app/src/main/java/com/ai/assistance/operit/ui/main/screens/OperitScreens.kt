package com.ai.assistance.operit.ui.main.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.about.screens.AboutScreen
import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.ui.features.help.screens.HelpScreen
import com.ai.assistance.operit.ui.features.packages.screens.PackageManagerScreen
import com.ai.assistance.operit.ui.features.problems.screens.ProblemLibraryScreen
import com.ai.assistance.operit.ui.features.settings.screens.ChatHistorySettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.FunctionalConfigScreen
import com.ai.assistance.operit.ui.features.settings.screens.LanguageSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ModelConfigScreen
import com.ai.assistance.operit.ui.features.settings.screens.ModelPromptsSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ThemeSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ToolPermissionSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesGuideScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesSettingsScreen
import com.ai.assistance.operit.ui.features.token.TokenConfigWebViewScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.AppPermissionsToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FileManagerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FormatConverterToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.LogcatToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ShellExecutorToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalAutoConfigToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ToolboxScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.UIDebuggerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ffmpegtoolbox.FFmpegToolboxScreen
import com.ai.assistance.operit.ui.features.voice.VoiceAssistantScreen

// 路由配置类
typealias ScreenNavigationHandler = (Screen) -> Unit

typealias NavItemChangeHandler = (NavItem) -> Unit

// 重构的Screen类，添加了路由相关属性和内容渲染函数
sealed class Screen(
        // 指定父屏幕，用于返回导航
        open val parentScreen: Screen? = null,
        // 对应的导航项，用于侧边栏高亮显示
        open val navItem: NavItem? = null,
        // 屏幕标题资源ID或字符串
        open val titleRes: String? = null
) {
        // 屏幕内容渲染函数
        @Composable
        open fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
                // 子类实现具体内容
        }

        // Main screens (primary)
        data object AiChat : Screen(navItem = NavItem.AiChat) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        AIChatScreen(
                                padding = PaddingValues(0.dp),
                                viewModel = null,
                                isFloatingMode = false,
                                hasBackgroundImage = hasBackgroundImage,
                                onNavigateToTokenConfig = { navigateTo(TokenConfig) },
                                onNavigateToSettings = {
                                        navigateTo(Settings)
                                        updateNavItem(NavItem.Settings)
                                },
                                onLoading = onLoading,
                                onError = onError,
                                onGestureConsumed = onGestureConsumed
                        )
                }
        }

        data object ProblemLibrary : Screen(navItem = NavItem.ProblemLibrary) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ProblemLibraryScreen()
                }
        }

        data object Packages : Screen(navItem = NavItem.Packages) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        PackageManagerScreen()
                }
        }

        data object Toolbox : Screen(navItem = NavItem.Toolbox) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ToolboxScreen(
                                navController = navController,
                                onFormatConverterSelected = { navigateTo(FormatConverter) },
                                onFileManagerSelected = { navigateTo(FileManager) },
                                onTerminalSelected = { navigateTo(Terminal) },
                                onTerminalAutoConfigSelected = { navigateTo(TerminalAutoConfig) },
                                onAppPermissionsSelected = { navigateTo(AppPermissions) },
                                onUIDebuggerSelected = { navigateTo(UIDebugger) },
                                onFFmpegToolboxSelected = { navigateTo(FFmpegToolbox) },
                                onShellExecutorSelected = { navigateTo(ShellExecutor) },
                                onLogcatSelected = { navigateTo(Logcat) }
                        )
                }
        }

        data object ShizukuCommands : Screen(navItem = NavItem.ShizukuCommands) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ShizukuDemoScreen()
                }
        }

        data object Settings : Screen(navItem = NavItem.Settings) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        SettingsScreen(
                                navigateToToolPermissions = { navigateTo(ToolPermission) },
                                onNavigateToUserPreferences = {
                                        navigateTo(UserPreferencesSettings)
                                },
                                navigateToModelConfig = { navigateTo(ModelConfig) },
                                navigateToThemeSettings = { navigateTo(ThemeSettings) },
                                navigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                                navigateToFunctionalConfig = { navigateTo(FunctionalConfig) },
                                navigateToChatHistorySettings = { navigateTo(ChatHistorySettings) },
                                navigateToLanguageSettings = { navigateTo(LanguageSettings) }
                        )
                }
        }

        data object Help : Screen(navItem = NavItem.Help) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        HelpScreen(
                                onBackPressed = {
                                        navigateTo(AiChat)
                                        updateNavItem(NavItem.AiChat)
                                }
                        )
                }
        }

        data object About : Screen(navItem = NavItem.About) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        AboutScreen()
                }
        }

        data object Agent : Screen(navItem = NavItem.Agent) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        VoiceAssistantScreen(
                                onNavigateToSettings = {
                                        navigateTo(Settings)
                                        updateNavItem(NavItem.Settings)
                                }
                        )
                }
        }

        data object TokenConfig : Screen(parentScreen = AiChat, navItem = NavItem.TokenConfig) {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        TokenConfigWebViewScreen(
                                onNavigateBack = {
                                        parentScreen?.let {
                                                navigateTo(it)
                                                it.navItem?.let { navItem ->
                                                        updateNavItem(navItem)
                                                }
                                        }
                                }
                        )
                }
        }

        // Secondary screens - Settings
        data object ToolPermission :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "工具权限") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ToolPermissionSettingsScreen(
                                navigateBack = {
                                        parentScreen?.let {
                                                navigateTo(it)
                                                it.navItem?.let { navItem ->
                                                        updateNavItem(navItem)
                                                }
                                        }
                                }
                        )
                }
        }

        data class UserPreferencesGuide(var profileName: String = "", var profileId: String = "") :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "用户偏好引导") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        UserPreferencesGuideScreen(
                                profileName = profileName,
                                profileId = profileId,
                                onComplete = {
                                        parentScreen?.let {
                                                navigateTo(it)
                                                it.navItem?.let { navItem ->
                                                        updateNavItem(navItem)
                                                }
                                        }
                                },
                                navigateToPermissions = {
                                        navigateTo(ShizukuCommands)
                                        updateNavItem(NavItem.ShizukuCommands)
                                }
                        )
                }
        }

        data object UserPreferencesSettings :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "用户偏好设置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        UserPreferencesSettingsScreen(
                                onNavigateBack = { parentScreen?.let { navigateTo(it) } },
                                onNavigateToGuide = { profileName, profileId ->
                                        navigateTo(UserPreferencesGuide(profileName, profileId))
                                }
                        )
                }
        }

        data object ModelConfig :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "模型与参数配置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ModelConfigScreen()
                }
        }

        data object ModelPromptsSettings :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "模型提示词设置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ModelPromptsSettingsScreen(
                                onBackPressed = {
                                        parentScreen?.let {
                                                navigateTo(it)
                                                it.navItem?.let { navItem ->
                                                        updateNavItem(navItem)
                                                }
                                        }
                                }
                        )
                }
        }

        data object FunctionalConfig :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "功能模型配置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        FunctionalConfigScreen(
                                onBackPressed = {
                                        parentScreen?.let {
                                                navigateTo(it)
                                                it.navItem?.let { navItem ->
                                                        updateNavItem(navItem)
                                                }
                                        }
                                }
                        )
                }
        }

        data object ThemeSettings :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "主题设置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ThemeSettingsScreen()
                }
        }

        data object ChatHistorySettings :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "聊天记录管理") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ChatHistorySettingsScreen()
                }
        }

        data object LanguageSettings :
                Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = "语言设置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        LanguageSettingsScreen(
                                onBackPressed = {
                                        parentScreen?.let {
                                                navigateTo(it)
                                                it.navItem?.let { navItem ->
                                                        updateNavItem(navItem)
                                                }
                                        }
                                }
                        )
                }
        }

        // Toolbox secondary screens
        data object FormatConverter :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "万能格式转换") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        FormatConverterToolScreen(navController = navController)
                }
        }

        data object FileManager :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "文件管理器") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        FileManagerToolScreen(navController = navController)
                }
        }

        data object Terminal :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "命令终端") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        TerminalToolScreen(navController = navController)
                }
        }

        data object TerminalAutoConfig :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "终端自动配置") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        TerminalAutoConfigToolScreen(navController = navController)
                }
        }

        data object AppPermissions :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "应用权限管理") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        AppPermissionsToolScreen(navController = navController)
                }
        }

        data object UIDebugger :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "UI调试工具") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        UIDebuggerToolScreen(navController = navController)
                }
        }

        data object ShellExecutor :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "命令执行器") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        ShellExecutorToolScreen(navController = navController)
                }
        }

        data object Logcat :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "日志查看器") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        LogcatToolScreen(navController = navController)
                }
        }

        // FFmpeg Toolbox screen
        data object FFmpegToolbox :
                Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = "FFmpeg工具箱") {
                @Composable
                override fun Content(
                        navController: NavController,
                        navigateTo: ScreenNavigationHandler,
                        updateNavItem: NavItemChangeHandler,
                        hasBackgroundImage: Boolean,
                        onLoading: (Boolean) -> Unit,
                        onError: (String) -> Unit,
                        onGestureConsumed: (Boolean) -> Unit
                ) {
                        FFmpegToolboxScreen(navController = navController)
                }
        }

        // 获取屏幕标题
        fun getTitle(): String = titleRes ?: ""

        // 判断是否为二级屏幕
        val isSecondaryScreen: Boolean
                get() = parentScreen != null
}

// 路由管理器
object OperitRouter {
        // 处理返回导航
        fun handleBackNavigation(currentScreen: Screen): Screen? {
                return currentScreen.parentScreen
        }

        // 根据NavItem获取对应的Screen
        fun getScreenForNavItem(navItem: NavItem): Screen {
                return when (navItem) {
                        NavItem.AiChat -> Screen.AiChat
                        NavItem.ProblemLibrary -> Screen.ProblemLibrary
                        NavItem.Packages -> Screen.Packages
                        NavItem.Toolbox -> Screen.Toolbox
                        NavItem.ShizukuCommands -> Screen.ShizukuCommands
                        NavItem.Settings -> Screen.Settings
                        NavItem.Help -> Screen.Help
                        NavItem.About -> Screen.About
                        NavItem.TokenConfig -> Screen.TokenConfig
                        NavItem.UserPreferencesGuide -> Screen.UserPreferencesGuide()
                        NavItem.Agent -> Screen.Agent
                        else -> Screen.AiChat
                }
        }
}

// 全局的手势状态持有者，用于在不同组件间共享手势状态
object GestureStateHolder {
        // 聊天界面手势是否被消费的状态
        var isChatScreenGestureConsumed: Boolean = false
}
