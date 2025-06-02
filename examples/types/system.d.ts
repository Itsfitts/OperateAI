/**
 * System operation type definitions for Assistance Package Tools
 */

import {
    SleepResultData, SystemSettingData, AppOperationData, AppListData,
    DeviceInfoResultData, NotificationData, LocationData,
    ADBResultData, IntentResultData, TerminalCommandResultData
} from './results';

/**
 * System operations namespace
 */
export namespace System {
    /**
     * Sleep for specified milliseconds
     * @param milliseconds - Milliseconds to sleep
     */
    function sleep(milliseconds: string | number): Promise<SleepResultData>;

    /**
     * Get a system setting
     * @param setting - Setting name
     * @param namespace - Setting namespace
     */
    function getSetting(setting: string, namespace?: string): Promise<SystemSettingData>;

    /**
     * Modify a system setting
     * @param setting - Setting name
     * @param value - New value
     * @param namespace - Setting namespace
     */
    function setSetting(setting: string, value: string, namespace?: string): Promise<SystemSettingData>;

    /**
     * Get device information
     */
    function getDeviceInfo(): Promise<DeviceInfoResultData>;

    /**
     * Stop a running app
     * @param packageName - Package name
     */
    function stopApp(packageName: string): Promise<AppOperationData>;

    /**
     * List installed apps
     * @param includeSystem - Whether to include system apps
     */
    function listApps(includeSystem?: boolean): Promise<AppListData>;

    /**
     * Start an app by package name
     * @param packageName - Package name
     * @param activity - Optional specific activity to launch
     */
    function startApp(packageName: string, activity?: string): Promise<AppOperationData>;

    /**
     * Get device notifications
     * @param limit - Maximum number of notifications to return (default: 10)
     * @param includeOngoing - Whether to include ongoing notifications (default: false)
     * @returns Promise resolving to notification data
     */
    function getNotifications(limit?: number, includeOngoing?: boolean): Promise<NotificationData>;

    /**
     * Get device location
     * @param highAccuracy - Whether to use high accuracy mode (default: false)
     * @param timeout - Timeout in seconds (default: 10)
     * @returns Promise resolving to location data
     */
    function getLocation(highAccuracy?: boolean, timeout?: number): Promise<LocationData>;

    /**
     * Execute an shell command (requires root access)
     * @param command The shell command to execute
     */
    function shell(command: string): Promise<ADBResultData>;

    /**
     * Execute an Intent
     * @param action - Intent action
     * @param uri - Intent URI
     * @param pkg - Package name
     * @param component - Component name
     * @param flags - Intent flags
     * @param extras - Intent extras (as object or stringified JSON)
     * @param type - Intent execution type: 'activity' (default), 'broadcast', or 'service'
     */
    function intent(
        action?: string,
        uri?: string,
        pkg?: string,
        component?: string,
        flags?: number | string,
        extras?: Record<string, any> | string,
        type?: 'activity' | 'broadcast' | 'service'
    ): Promise<IntentResultData>;

    /**
     * Execute a terminal command and collect the output
     * @param command The command to execute
     * @param sessionId Optional session ID to use a specific terminal session
     * @param timeoutMs Optional timeout in milliseconds
     * @returns Promise resolving to the result of the terminal command execution
     */
    function terminal(command: string, sessionId?: string, timeoutMs?: number): Promise<TerminalCommandResultData>;
}