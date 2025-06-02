/*
METADATA
{
    "name": "super_admin",
    "description": "超级管理员工具集，提供终端命令和Shell操作的高级功能。可以执行系统命令，保持目录上下文，并支持多种终端操作模式。适合需要进行底层系统管理和命令行操作的场景。",
    "tools": [
        {
            "name": "terminal",
            "description": "通过Termux终端执行命令并收集输出结果，会自动保留目录上下文",
            "parameters": [
                {
                    "name": "command",
                    "description": "要执行的命令",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "sessionId",
                    "description": "可选的会话ID，用于使用特定的终端会话",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeoutMs",
                    "description": "可选的超时时间（毫秒）",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "shell",
            "description": "使用ADB或Root权限执行Shell命令，适用于需要系统级权限的操作",
            "parameters": [
                {
                    "name": "command",
                    "description": "要执行的Shell命令",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ],
    "category": "SYSTEM_OPERATION"
}
*/

const superAdmin = (function () {
    /**
     * 执行终端命令并收集输出结果
     * @param command - 要执行的命令
     * @param sessionId - 可选的会话ID，用于使用特定的终端会话
     * @param timeoutMs - 可选的超时时间（毫秒）
     */
    async function terminal(params: { command: string, sessionId?: string, timeoutMs?: string }): Promise<any> {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }

            const command = params.command;
            const sessionId = params.sessionId;
            const timeoutMs = params.timeoutMs;

            console.log(`执行终端命令: ${command}`);

            // 将超时时间转换为数字类型
            const timeout = timeoutMs ? parseInt(timeoutMs, 10) : undefined;

            // 调用系统工具执行终端命令
            const result = await Tools.System.terminal(command, sessionId, timeout);

            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode,
                sessionId: result.sessionId || sessionId,
                context_preserved: true // 标记此命令保留了目录上下文
            };
        } catch (error) {
            console.error(`[terminal] 错误: ${error.message}`);
            console.error(error.stack);

            throw error;
        }
    }

    /**
     * 执行Shell命令
     * @param command - 要执行的Shell命令
     * @param timeoutMs - 可选的超时时间（毫秒）
     */
    async function shell(params: { command: string }): Promise<any> {
        try {
            if (!params.command) {
                throw new Error("命令不能为空");
            }
            const command = params.command;

            console.log(`执行Shell命令: ${command}`);

            // 使用ADB命令执行shell操作
            const result = await Tools.System.shell(`${command}`);

            return {
                command: command,
                output: result.output,
                exitCode: result.exitCode
            };
        } catch (error) {
            console.error(`[shell] 错误: ${error.message}`);
            console.error(error.stack);

            throw error;
        }
    }

    return {
        terminal,
        shell
    };
})();

// 逐个导出
exports.terminal = superAdmin.terminal;
exports.shell = superAdmin.shell; 