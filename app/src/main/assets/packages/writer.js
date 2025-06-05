/* METADATA
{
  name: writer
  description: 建议在编写项目的时候激活，是file_writer的升级版，提供高级文件编辑和读取功能，支持分段编辑、差异编辑、行号编辑以及高级文件读取操作，如分段读取、正则表达式匹配、函数块提取等。
  
  tools: [
    {
      name: read_lines
      description: 读取文件的指定行范围
      parameters: [
        {
          name: file_path
          description: 要读取的文件路径
          type: string
          required: true
        },
        {
          name: start_line
          description: 起始行号(从1开始计数)
          type: string
          required: true
        },
        {
          name: end_line
          description: 结束行号(包含在内)
          type: string
          required: true
        }
      ]
    },
    {
      name: read_regex_matches
      description: 读取文件中匹配正则表达式的内容
      parameters: [
        {
          name: file_path
          description: 要读取的文件路径
          type: string
          required: true
        },
        {
          name: regex
          description: 正则表达式字符串
          type: string
          required: true
        },
        {
          name: include_line_numbers
          description: 是否在结果中包含行号(true/false)
          type: string
          required: false
        }
      ]
    },
    {
      name: edit_lines
      description: 编辑文件的指定行范围
      parameters: [
        {
          name: file_path
          description: 要编辑的文件路径
          type: string
          required: true
        },
        {
          name: start_line
          description: 起始行号(从1开始计数)
          type: string
          required: true
        },
        {
          name: end_line
          description: 结束行号(包含在内)
          type: string
          required: true
        },
        {
          name: content
          description: 用于替换指定行的新内容
          type: string
          required: true
        }
      ]
    },
    {
      name: insert_at_line
      description: 在指定行插入内容
      parameters: [
        {
          name: file_path
          description: 要编辑的文件路径
          type: string
          required: true
        },
        {
          name: line_number
          description: 要插入内容的行号(从1开始计数)
          type: string
          required: true
        },
        {
          name: content
          description: 要插入的内容
          type: string
          required: true
        },
        {
          name: before
          description: 是否在指定行之前插入(true/false，默认为false)
          type: string
          required: false
        }
      ]
    },
    {
      name: extract_function
      description: 提取文件中的函数或方法块
      parameters: [
        {
          name: file_path
          description: 要读取的文件路径
          type: string
          required: true
        },
        {
          name: function_name
          description: 要提取的函数或方法名称
          type: string
          required: true
        },
        {
          name: include_line_numbers
          description: 是否在结果中包含行号(true/false)
          type: string
          required: false
        }
      ]
    },
    {
      name: diff_edit
      description: 使用差异对比方式编辑文件内容
      parameters: [
        {
          name: file_path
          description: 要编辑的文件路径
          type: string
          required: true
        },
        {
          name: original_text
          description: 原始文本内容
          type: string
          required: true
        },
        {
          name: new_text
          description: 新的文本内容
          type: string
          required: true
        }
      ]
    },
    {
      name: batch_replace
      description: 批量替换文件中的内容
      parameters: [
        {
          name: file_path
          description: 要编辑的文件路径
          type: string
          required: true
        },
        {
          name: replacements
          description: 替换项数组，JSON格式，每个替换项包含pattern和replacement
          type: string
          required: true
        },
        {
          name: use_regex
          description: 是否使用正则表达式进行替换(true/false)
          type: string
          required: false
        }
      ]
    },
    {
      name: process_large_file
      description: 分块处理大文件
      parameters: [
        {
          name: file_path
          description: 要处理的文件路径
          type: string
          required: true
        },
        {
          name: chunk_size
          description: 每个块的行数
          type: string
          required: false
        },
        {
          name: start_line
          description: 开始处理的行号(从1开始)
          type: string
          required: false
        },
        {
          name: max_chunks
          description: 最大处理的块数
          type: string
          required: false
        }
      ]
    },
    {
      name: extract_code_block
      description: 提取文件中的代码块
      parameters: [
        {
          name: file_path
          description: 要读取的文件路径
          type: string
          required: true
        },
        {
          name: block_start
          description: 代码块开始标识(例如 '{'、'```' 等)
          type: string
          required: true
        },
        {
          name: block_end
          description: 代码块结束标识(例如 '}'、'```' 等)
          type: string
          required: true
        },
        {
          name: context_lines
          description: 提取代码块时包含的上下文行数
          type: string
          required: false
        }
      ]
    },
    {
      name: find_nested_structures
      description: 查找嵌套结构(如括号嵌套、标签嵌套等)
      parameters: [
        {
          name: file_path
          description: 要读取的文件路径
          type: string
          required: true
        },
        {
          name: open_pattern
          description: 开始标识(如 '{', '(', '<div>' 等)
          type: string
          required: true
        },
        {
          name: close_pattern
          description: 结束标识(如 '}', ')', '</div>' 等)
          type: string
          required: true
        },
        {
          name: max_depth
          description: 最大嵌套深度限制
          type: string
          required: false
        }
      ]
    }
  ]
  
  category: FILE_WRITE
}
*/
const writer = (function () {
    /**
     * 参数类型转换函数 - 将输入参数转换为期望的数据类型
     * @param params 输入参数对象
     * @param paramTypes 参数类型定义
     * @returns 转换后的参数对象
     */
    function convertParamTypes(params, paramTypes) {
        if (!params || !paramTypes)
            return params;
        const result = {};
        for (const key in params) {
            if (params[key] === undefined || params[key] === null) {
                result[key] = params[key];
                continue;
            }
            const expectedType = paramTypes[key];
            if (!expectedType) {
                // 如果没有指定类型，保持原样
                result[key] = params[key];
                continue;
            }
            // 获取参数值
            const value = params[key];
            try {
                switch (expectedType.toLowerCase()) {
                    case 'number':
                        // 将字符串转换为数字
                        if (typeof value === 'string') {
                            if (value.includes('.')) {
                                result[key] = parseFloat(value);
                            }
                            else {
                                result[key] = parseInt(value, 10);
                            }
                            // 检查转换结果是否为有效数字
                            if (isNaN(result[key])) {
                                throw new Error(`参数 ${key} 无法转换为数字: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    case 'boolean':
                        // 将字符串转换为布尔值
                        if (typeof value === 'string') {
                            const lowerValue = value.toLowerCase();
                            if (lowerValue === 'true' || lowerValue === '1' || lowerValue === 'yes') {
                                result[key] = true;
                            }
                            else if (lowerValue === 'false' || lowerValue === '0' || lowerValue === 'no') {
                                result[key] = false;
                            }
                            else {
                                throw new Error(`参数 ${key} 无法转换为布尔值: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    case 'array':
                        // 将字符串转换为数组
                        if (typeof value === 'string') {
                            try {
                                result[key] = JSON.parse(value);
                                if (!Array.isArray(result[key])) {
                                    throw new Error('解析结果不是数组');
                                }
                            }
                            catch (e) {
                                throw new Error(`参数 ${key} 无法转换为数组: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    case 'object':
                        // 将字符串转换为对象
                        if (typeof value === 'string') {
                            try {
                                result[key] = JSON.parse(value);
                                if (Array.isArray(result[key]) || typeof result[key] !== 'object') {
                                    throw new Error('解析结果不是对象');
                                }
                            }
                            catch (e) {
                                throw new Error(`参数 ${key} 无法转换为对象: ${value}`);
                            }
                        }
                        else {
                            result[key] = value;
                        }
                        break;
                    default:
                        // 其他类型或未指定类型，保持原样
                        result[key] = value;
                }
            }
            catch (error) {
                console.error(`参数类型转换错误: ${error.message}`);
                // 转换失败时保留原始值
                result[key] = value;
            }
        }
        return result;
    }
    /**
     * 读取文件的指定行范围
     * @param {Object} params - 包含文件路径和行范围的参数对象
     * @returns {Promise<Object>} 包含读取结果的Promise
     */
    async function read_lines(params) {
        // 定义参数类型
        const paramTypes = {
            start_line: 'string',
            end_line: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, start_line, end_line } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (start_line < 1) {
            complete("起始行号必须大于或等于1");
            return;
        }
        if (end_line < start_line) {
            complete("结束行号必须大于或等于起始行号");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`,
                    content: null
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`,
                    content: null
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 验证行号范围
            if (start_line > lines.length) {
                complete({
                    success: false,
                    message: `起始行号超出文件范围，文件总行数: ${lines.length}`,
                    content: null
                });
                return;
            }
            // 限制结束行号不超过文件总行数
            const actualEndLine = Math.min(end_line, lines.length);
            // 提取指定范围的行（注意：索引从0开始，而行号从1开始）
            const selectedLines = lines.slice(start_line - 1, actualEndLine);
            complete({
                success: true,
                message: `成功读取${file_path}的第${start_line}行至第${actualEndLine}行`,
                content: selectedLines.join('\n'),
                start_line: start_line,
                end_line: actualEndLine,
                total_lines: lines.length
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `读取文件时出错: ${error.message}`,
                content: null
            });
        }
    }
    /**
     * 读取文件中匹配正则表达式的内容
     * @param {Object} params - 包含文件路径和正则表达式的参数对象
     * @returns {Promise<Object>} 包含匹配结果的Promise
     */
    async function read_regex_matches(params) {
        const { file_path, regex, include_line_numbers = false } = params;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (!regex || regex.trim() === "") {
            complete("请提供有效的正则表达式");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`,
                    matches: []
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`,
                    matches: []
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 创建正则表达式对象
            const regexObj = new RegExp(regex, 'g');
            // 存储匹配结果
            const matches = [];
            // 遍历每一行，查找匹配项
            for (let i = 0; i < lines.length; i++) {
                const line = lines[i];
                const lineMatches = [];
                let match;
                // 重置正则表达式状态，以便在同一行中查找多个匹配项
                regexObj.lastIndex = 0;
                while ((match = regexObj.exec(line)) !== null) {
                    lineMatches.push({
                        text: match[0],
                        index: match.index,
                        groups: match.slice(1)
                    });
                }
                if (lineMatches.length > 0) {
                    if (include_line_numbers) {
                        matches.push({
                            line_number: i + 1,
                            line_content: line,
                            matches: lineMatches
                        });
                    }
                    else {
                        matches.push(...lineMatches.map(m => m.text));
                    }
                }
            }
            complete({
                success: true,
                message: `在${file_path}中找到${matches.length}个匹配项`,
                matches: matches
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `查找匹配项时出错: ${error.message}`,
                matches: []
            });
        }
    }
    /**
     * 编辑文件的指定行范围
     * @param {Object} params - 包含文件路径、行范围和新内容的参数对象
     * @returns {Promise<Object>} 包含编辑结果的Promise
     */
    async function edit_lines(params) {
        const { file_path, start_line, end_line, content } = params;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (start_line < 1) {
            complete("起始行号必须大于或等于1");
            return;
        }
        if (end_line < start_line) {
            complete("结束行号必须大于或等于起始行号");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 验证行号范围
            if (start_line > lines.length) {
                complete({
                    success: false,
                    message: `起始行号超出文件范围，文件总行数: ${lines.length}`
                });
                return;
            }
            // 计算要替换的行数
            const actualEndLine = Math.min(end_line, lines.length);
            const replaceLinesCount = actualEndLine - start_line + 1;
            // 将新内容分割为行
            const newLines = content.split('\n');
            // 替换指定范围的行
            lines.splice(start_line - 1, replaceLinesCount, ...newLines);
            // 将修改后的内容写回文件
            await Tools.Files.write(file_path, lines.join('\n'));
            complete({
                success: true,
                message: `成功编辑${file_path}的第${start_line}行至第${actualEndLine}行`,
                replaced_lines: replaceLinesCount,
                new_lines: newLines.length
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `编辑文件时出错: ${error.message}`
            });
        }
    }
    /**
     * 在指定行插入内容
     * @param {Object} params - 包含文件路径、行号和要插入内容的参数对象
     * @returns {Promise<Object>} 包含插入结果的Promise
     */
    async function insert_at_line(params) {
        // 定义参数类型
        const paramTypes = {
            line_number: 'string',
            before: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, line_number, content, before = false } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (line_number < 1) {
            complete("行号必须大于或等于1");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 验证行号范围
            if (line_number > lines.length + 1) {
                complete({
                    success: false,
                    message: `行号超出文件范围，文件总行数: ${lines.length}`
                });
                return;
            }
            // 将要插入的内容分割为行
            const insertLines = content.split('\n');
            // 确定插入位置
            const insertPosition = before ? line_number - 1 : line_number;
            // 插入内容
            lines.splice(insertPosition, 0, ...insertLines);
            // 将修改后的内容写回文件
            await Tools.Files.write(file_path, lines.join('\n'));
            complete({
                success: true,
                message: `成功在${file_path}的第${line_number}行${before ? '之前' : '之后'}插入内容`,
                inserted_lines: insertLines.length
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `插入内容时出错: ${error.message}`
            });
        }
    }
    /**
     * 提取文件中的函数或方法块
     * @param {Object} params - 包含文件路径和函数名称的参数对象
     * @returns {Promise<Object>} 包含提取结果的Promise
     */
    async function extract_function(params) {
        // 定义参数类型
        const paramTypes = {
            include_line_numbers: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, function_name, include_line_numbers = false } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (!function_name || function_name.trim() === "") {
            complete("请提供有效的函数名称");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`,
                    content: null
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`,
                    content: null
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 查找函数定义开始位置
            const functionPatterns = [
                // 函数声明
                new RegExp(`\\bfunction\\s+${function_name}\\s*\\(`),
                // 箭头函数
                new RegExp(`\\b${function_name}\\s*=\\s*\\([^)]*\\)\\s*=>`),
                // 对象方法
                new RegExp(`\\b${function_name}\\s*\\(`),
                // 类方法
                new RegExp(`\\b(async\\s+)?${function_name}\\s*\\(`)
            ];
            let startLine = -1;
            let endLine = -1;
            let bracketCount = 0;
            let foundStart = false;
            // 查找函数定义开始行
            for (let i = 0; i < lines.length; i++) {
                const line = lines[i];
                // 判断是否找到函数定义
                if (!foundStart) {
                    const isMatch = functionPatterns.some(pattern => pattern.test(line));
                    if (isMatch) {
                        startLine = i + 1; // 行号从1开始
                        foundStart = true;
                        bracketCount += (line.match(/{/g) || []).length;
                        bracketCount -= (line.match(/}/g) || []).length;
                        // 如果函数定义和结束在同一行
                        if (bracketCount === 0 && line.includes('{') && line.includes('}')) {
                            endLine = i + 1;
                            break;
                        }
                        continue;
                    }
                }
                // 找到开始后，计算大括号的配对情况
                if (foundStart) {
                    bracketCount += (line.match(/{/g) || []).length;
                    bracketCount -= (line.match(/}/g) || []).length;
                    // 当括号配对完成时，找到函数结束位置
                    if (bracketCount === 0) {
                        endLine = i + 1;
                        break;
                    }
                }
            }
            if (startLine === -1) {
                complete({
                    success: false,
                    message: `在文件${file_path}中找不到函数${function_name}的定义`,
                    content: null
                });
                return;
            }
            // 如果没有找到结束位置但找到了开始位置，可能是因为代码结构不标准
            if (endLine === -1 && startLine !== -1) {
                complete({
                    success: false,
                    message: `无法确定函数${function_name}的结束位置，可能是由于代码结构不标准`,
                    content: null,
                    start_line: startLine
                });
                return;
            }
            // 提取函数内容
            const functionContent = lines.slice(startLine - 1, endLine);
            const result = {
                success: true,
                message: `成功提取函数${function_name}`,
                content: functionContent.join('\n'),
                start_line: startLine,
                end_line: endLine
            };
            if (include_line_numbers) {
                result.lines = functionContent.map((line, index) => ({
                    line_number: startLine + index,
                    content: line
                }));
            }
            complete(result);
        }
        catch (error) {
            complete({
                success: false,
                message: `提取函数时出错: ${error.message}`,
                content: null
            });
        }
    }
    /**
     * 使用差异对比方式编辑文件内容
     * @param {Object} params - 包含文件路径、原始文本和新文本的参数对象
     * @returns {Promise<Object>} 包含编辑结果的Promise
     */
    async function diff_edit(params) {
        const { file_path, original_text, new_text } = params;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`
                });
                return;
            }
            // 文件内容
            const fileContent = fileResult.content;
            // 检查原始文本是否存在于文件中
            if (!fileContent.includes(original_text)) {
                complete({
                    success: false,
                    message: `在文件${file_path}中找不到指定的原始文本`,
                    found_in_file: false
                });
                return;
            }
            // 替换文本
            const newContent = fileContent.replace(original_text, new_text);
            // 写回文件
            await Tools.Files.write(file_path, newContent);
            complete({
                success: true,
                message: `成功使用差异对比方式编辑文件${file_path}`,
                chars_replaced: original_text.length,
                chars_added: new_text.length
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `差异编辑文件时出错: ${error.message}`
            });
        }
    }
    /**
     * 批量替换文件中的内容
     * @param {Object} params - 包含文件路径和替换项的参数对象
     * @returns {Promise<Object>} 包含替换结果的Promise
     */
    async function batch_replace(params) {
        // 定义参数类型
        const paramTypes = {
            replacements: 'string',
            use_regex: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, replacements, use_regex = false } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (!Array.isArray(replacements) || replacements.length === 0) {
            complete("请提供有效的替换项数组");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`
                });
                return;
            }
            // 文件内容
            let fileContent = fileResult.content;
            // 使用明确的类型声明替代隐式的never类型
            const replacementResults = [];
            // 遍历替换项进行替换
            for (const item of replacements) {
                const { pattern, replacement } = item;
                if (!pattern) {
                    replacementResults.push({
                        pattern,
                        success: false,
                        message: "无效的模式"
                    });
                    continue;
                }
                try {
                    let replacedContent;
                    let matchCount = 0;
                    if (use_regex) {
                        // 使用正则表达式替换
                        const regex = new RegExp(pattern, 'g');
                        // 计算匹配数量
                        matchCount = (fileContent.match(regex) || []).length;
                        // 执行替换
                        replacedContent = fileContent.replace(regex, replacement);
                    }
                    else {
                        // 使用字符串替换
                        // 计算匹配数量
                        let count = 0;
                        let pos = -1;
                        while ((pos = fileContent.indexOf(pattern, pos + 1)) !== -1) {
                            count++;
                        }
                        matchCount = count;
                        // 执行替换
                        replacedContent = fileContent.split(pattern).join(replacement);
                    }
                    // 更新文件内容
                    fileContent = replacedContent;
                    replacementResults.push({
                        pattern,
                        success: true,
                        matches: matchCount
                    });
                }
                catch (error) {
                    replacementResults.push({
                        pattern,
                        success: false,
                        message: error.message
                    });
                }
            }
            // 将修改后的内容写回文件
            await Tools.Files.write(file_path, fileContent);
            complete({
                success: true,
                message: `成功批量替换${file_path}的内容`,
                results: replacementResults
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `批量替换文件时出错: ${error.message}`
            });
        }
    }
    /**
     * 分块处理大文件
     * @param {Object} params - 包含文件路径和处理参数的对象
     * @returns {Promise<Object>} 包含处理结果的Promise
     */
    async function process_large_file(params) {
        // 定义参数类型
        const paramTypes = {
            chunk_size: 'string',
            start_line: 'string',
            max_chunks: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, chunk_size = 1000, start_line = 1, max_chunks = 5 } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`
                });
                return;
            }
            // 首先尝试读取文件以确定其大小
            // 对于小文件，我们将直接处理其内容
            // 对于大文件，我们将使用流式处理方式
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`
                });
                return;
            }
            // 文件内容
            const fileContent = fileResult.content;
            // 估算文件大小 (按字节计算)
            const fileSize = fileContent.length;
            // 将文件内容分割为行
            const lines = fileContent.split('\n');
            const totalLines = lines.length;
            // 如果文件过大，使用分块处理
            if (fileSize > 10 * 1024 * 1024) { // 大于10MB的文件
                // 计算将要处理的块数
                const chunksNeeded = Math.ceil((totalLines - start_line + 1) / chunk_size);
                const chunksToProcess = Math.min(chunksNeeded, max_chunks);
                // 构建结果
                const chunks = [];
                // 处理每个块
                for (let i = 0; i < chunksToProcess; i++) {
                    const chunkStartLine = start_line + i * chunk_size;
                    const chunkEndLine = Math.min(chunkStartLine + chunk_size - 1, totalLines);
                    const selectedLines = lines.slice(chunkStartLine - 1, chunkEndLine);
                    chunks.push({
                        chunk_index: i,
                        start_line: chunkStartLine,
                        end_line: chunkEndLine,
                        status: "processed",
                        line_count: selectedLines.length
                    });
                }
                complete({
                    success: true,
                    message: `成功处理大文件 ${file_path} 的部分内容`,
                    file_size: fileSize,
                    total_lines: totalLines,
                    chunks_processed: chunksToProcess,
                    total_chunks: chunksNeeded,
                    remaining_chunks: chunksNeeded - chunksToProcess,
                    next_start_line: start_line + chunksToProcess * chunk_size,
                    chunks: chunks
                });
            }
            else {
                // 对于较小的文件，直接处理全部内容
                // 计算将要处理的块数
                const chunksNeeded = Math.ceil((totalLines - start_line + 1) / chunk_size);
                const chunksToProcess = Math.min(chunksNeeded, max_chunks);
                // 构建结果
                const chunks = [];
                // 处理每个块
                for (let i = 0; i < chunksToProcess; i++) {
                    const chunkStartLine = start_line + i * chunk_size;
                    const chunkEndLine = Math.min(chunkStartLine + chunk_size - 1, totalLines);
                    chunks.push({
                        chunk_index: i,
                        start_line: chunkStartLine,
                        end_line: chunkEndLine,
                        status: "processed",
                        line_count: chunkEndLine - chunkStartLine + 1,
                        content: lines.slice(chunkStartLine - 1, chunkEndLine).join('\n')
                    });
                }
                complete({
                    success: true,
                    message: `成功处理文件 ${file_path} 的全部内容`,
                    file_size: fileSize,
                    total_lines: totalLines,
                    chunks_processed: chunksToProcess,
                    chunks: chunks
                });
            }
        }
        catch (error) {
            complete({
                success: false,
                message: `处理大文件时出错: ${error.message}`
            });
        }
    }
    /**
     * 提取文件中的代码块
     * @param {Object} params - 包含文件路径和代码块标识的参数对象
     * @returns {Promise<Object>} 包含提取结果的Promise
     */
    async function extract_code_block(params) {
        // 定义参数类型
        const paramTypes = {
            context_lines: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, block_start, block_end, context_lines = 0 } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (!block_start || !block_end) {
            complete("请提供有效的代码块开始和结束标识");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`,
                    blocks: []
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`,
                    blocks: []
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 存储找到的代码块
            const codeBlocks = [];
            // 遍历文件行查找代码块
            let inBlock = false;
            let blockStartLine = -1;
            let blockContent = [];
            for (let i = 0; i < lines.length; i++) {
                const line = lines[i];
                if (!inBlock && line.includes(block_start)) {
                    // 找到代码块开始
                    inBlock = true;
                    blockStartLine = i + 1; // 行号从1开始
                    blockContent = [line];
                }
                else if (inBlock) {
                    // 在代码块内
                    blockContent.push(line);
                    if (line.includes(block_end)) {
                        // 找到代码块结束
                        inBlock = false;
                        const blockEndLine = i + 1;
                        // 获取上下文
                        const contextBeforeStart = Math.max(0, blockStartLine - 1 - context_lines);
                        const contextAfterEnd = Math.min(lines.length - 1, blockEndLine + context_lines - 1);
                        const contextBefore = context_lines > 0 ?
                            lines.slice(contextBeforeStart, blockStartLine - 1) : undefined;
                        const contextAfter = context_lines > 0 ?
                            lines.slice(blockEndLine, contextAfterEnd + 1) : undefined;
                        // 保存代码块
                        codeBlocks.push({
                            start_line: blockStartLine,
                            end_line: blockEndLine,
                            context_before: contextBefore,
                            context_after: contextAfter,
                            content: blockContent.join('\n')
                        });
                        // 重置
                        blockContent = [];
                    }
                }
            }
            complete({
                success: true,
                message: `在文件 ${file_path} 中找到 ${codeBlocks.length} 个代码块`,
                blocks: codeBlocks
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `提取代码块时出错: ${error.message}`,
                blocks: []
            });
        }
    }
    /**
     * 查找文件中的嵌套结构
     * @param {Object} params - 包含文件路径和嵌套结构标识的参数对象
     * @returns {Promise<Object>} 包含查找结果的Promise
     */
    async function find_nested_structures(params) {
        // 定义参数类型
        const paramTypes = {
            max_depth: 'string'
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { file_path, open_pattern, close_pattern, max_depth = 100 } = convertedParams;
        if (!file_path || file_path.trim() === "") {
            complete("请提供有效的文件路径");
            return;
        }
        if (!open_pattern || !close_pattern) {
            complete("请提供有效的开始和结束标识");
            return;
        }
        try {
            // 检查文件是否存在
            const fileExists = await Tools.Files.exists(file_path);
            if (!fileExists || !fileExists.exists) {
                complete({
                    success: false,
                    message: `文件不存在: ${file_path}`,
                    structures: []
                });
                return;
            }
            // 读取整个文件内容
            const fileResult = await Tools.Files.read(file_path);
            if (!fileResult || !fileResult.content) {
                complete({
                    success: false,
                    message: `无法读取文件内容: ${file_path}`,
                    structures: []
                });
                return;
            }
            // 将文件内容分割为行
            const lines = fileResult.content.split('\n');
            // 存储找到的嵌套结构
            const nestedStructures = [];
            const stack = [];
            // 正则表达式转义特殊字符
            const escapeRegExp = (str) => {
                return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            };
            const openRegex = new RegExp(escapeRegExp(open_pattern), 'g');
            const closeRegex = new RegExp(escapeRegExp(close_pattern), 'g');
            // 遍历文件行查找嵌套结构
            for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                const line = lines[lineIndex];
                let match;
                // 查找开始标识
                openRegex.lastIndex = 0;
                while ((match = openRegex.exec(line)) !== null) {
                    if (stack.length >= max_depth) {
                        // 达到最大深度限制，不再继续添加
                        continue;
                    }
                    stack.push({
                        start_line: lineIndex + 1,
                        start_char: match.index,
                        line_content: line
                    });
                }
                // 查找结束标识
                closeRegex.lastIndex = 0;
                while ((match = closeRegex.exec(line)) !== null) {
                    if (stack.length > 0) {
                        // 找到配对的开始标识
                        const openMarker = stack.pop();
                        const startLine = openMarker.start_line;
                        const endLine = lineIndex + 1;
                        // 提取嵌套结构内容
                        let structureContent = '';
                        if (startLine === endLine) {
                            // 在同一行内
                            const startChar = openMarker.start_char;
                            const endChar = match.index + close_pattern.length;
                            structureContent = line.substring(startChar, endChar);
                        }
                        else {
                            // 跨越多行
                            const firstLine = openMarker.line_content.substring(openMarker.start_char);
                            const lastLine = line.substring(0, match.index + close_pattern.length);
                            const middleLines = lines.slice(startLine, lineIndex).join('\n');
                            structureContent = firstLine + '\n' + middleLines + '\n' + lastLine;
                        }
                        // 添加到结果
                        nestedStructures.push({
                            start_line: startLine,
                            end_line: endLine,
                            depth: stack.length + 1, // 当前深度
                            content: structureContent
                        });
                    }
                }
            }
            // 如果还有未配对的开始标识，报告为不完整的结构
            const incompleteStructures = stack.map(marker => ({
                start_line: marker.start_line,
                incomplete: true
            }));
            complete({
                success: true,
                message: `在文件 ${file_path} 中找到 ${nestedStructures.length} 个嵌套结构`,
                structures: nestedStructures,
                incomplete_structures: incompleteStructures,
                max_depth_reached: stack.length >= max_depth
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `查找嵌套结构时出错: ${error.message}`,
                structures: []
            });
        }
    }
    /**
     * 包装函数 - 统一处理所有Writer函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     */
    async function writer_wrap(func, params, successMessage, failMessage) {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));
            // 执行原始函数
            const result = await func(params);
            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));
            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined)
                return;
            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage
                });
            }
            else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    data: result
                });
            }
        }
        catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);
            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                error_stack: error.stack
            });
        }
    }
    return {
        read_lines: async (params) => await writer_wrap(read_lines, params, "成功读取指定行范围", "读取指定行范围失败"),
        read_regex_matches: async (params) => await writer_wrap(read_regex_matches, params, "成功读取正则表达式匹配内容", "读取正则表达式匹配内容失败"),
        edit_lines: async (params) => await writer_wrap(edit_lines, params, "成功编辑指定行范围", "编辑指定行范围失败"),
        insert_at_line: async (params) => await writer_wrap(insert_at_line, params, "成功在指定行插入内容", "在指定行插入内容失败"),
        extract_function: async (params) => await writer_wrap(extract_function, params, "成功提取函数", "提取函数失败"),
        diff_edit: async (params) => await writer_wrap(diff_edit, params, "成功差异编辑文件", "差异编辑文件失败"),
        batch_replace: async (params) => await writer_wrap(batch_replace, params, "成功批量替换文件内容", "批量替换文件内容失败"),
        process_large_file: async (params) => await writer_wrap(process_large_file, params, "成功处理大文件", "处理大文件失败"),
        extract_code_block: async (params) => await writer_wrap(extract_code_block, params, "成功提取代码块", "提取代码块失败"),
        find_nested_structures: async (params) => await writer_wrap(find_nested_structures, params, "成功查找嵌套结构", "查找嵌套结构失败")
    };
})();
// 导出所有函数
exports.read_lines = writer.read_lines;
exports.read_regex_matches = writer.read_regex_matches;
exports.edit_lines = writer.edit_lines;
exports.insert_at_line = writer.insert_at_line;
exports.extract_function = writer.extract_function;
exports.diff_edit = writer.diff_edit;
exports.batch_replace = writer.batch_replace;
exports.process_large_file = writer.process_large_file;
exports.extract_code_block = writer.extract_code_block;
exports.find_nested_structures = writer.find_nested_structures;
