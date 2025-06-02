/* METADATA
{
  name: various_search
  description: 提供多平台搜索功能，支持从必应、百度、搜狗、夸克等平台获取搜索结果。
  
  tools: [
    {
      name: search_bing
      description: 使用必应搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        }
      ]
    },
    {
      name: search_baidu
      description: 使用百度搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_sogou
      description: 使用搜狗搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_quark
      description: 使用夸克搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: combined_search
      description: 在多个平台同时执行搜索。建议用户要求搜索的时候默认使用这个工具。
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: platforms
          description: 搜索平台列表字符串，可选值包括"bing","baidu","sogou","quark"，多个平台用逗号分隔，比如"bing,baidu"
          type: string
          required: true
        }
      ]
    }
  ]
  
  category: NETWORK
}
*/
const various_search = (function () {
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
     * 包装函数 - 统一处理所有搜索函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     */
    async function search_wrap(func, params) {
        const successMessage = `成功执行${func.name || '搜索'}操作`;
        const failMessage = `${func.name || '搜索'}操作失败`;
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));
            // 执行原始函数
            const result = await func(params);
            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));
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
    /**
     * 使用必应搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_bing(params) {
        const { query } = params;
        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }
        try {
            // 构建必应搜索URL
            const encodedQuery = encodeURIComponent(query);
            const url = `https://cn.bing.com/search?q=${encodedQuery}&FORM=HDRSC1`;
            // 访问搜索页面
            const response = await Tools.Net.visit(url);
            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取必应搜索结果`
                };
            }
            // 返回搜索结果
            return {
                success: true,
                message: `成功从必应获取搜索结果`,
                query: query,
                content: response.content
            };
        }
        catch (error) {
            return {
                success: false,
                message: `必应搜索时出错: ${error.message}`
            };
        }
    }
    /**
     * 使用百度搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_baidu(params) {
        // 定义参数类型
        const paramTypes = {
            page: 'number',
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { query, page = 1 } = convertedParams;
        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }
        try {
            // 构建百度搜索URL
            const pn = (page - 1) * 10; // 百度分页参数
            const encodedQuery = encodeURIComponent(query);
            const url = `https://www.baidu.com/s?wd=${encodedQuery}&pn=${pn}`;
            // 访问搜索页面
            const response = await Tools.Net.visit(url);
            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取百度搜索结果`
                };
            }
            // 返回搜索结果
            return {
                success: true,
                message: `成功从百度获取搜索结果`,
                query: query,
                page: page,
                content: response.content
            };
        }
        catch (error) {
            return {
                success: false,
                message: `百度搜索时出错: ${error.message}`
            };
        }
    }
    /**
     * 使用搜狗搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_sogou(params) {
        // 定义参数类型
        const paramTypes = {
            page: 'number',
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { query, page = 1 } = convertedParams;
        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }
        try {
            // 构建搜狗搜索URL
            const encodedQuery = encodeURIComponent(query);
            const url = `https://www.sogou.com/web?query=${encodedQuery}&page=${page}`;
            // 访问搜索页面
            const response = await Tools.Net.visit(url);
            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取搜狗搜索结果`
                };
            }
            // 返回搜索结果
            return {
                success: true,
                message: `成功从搜狗获取搜索结果`,
                query: query,
                page: page,
                content: response.content
            };
        }
        catch (error) {
            return {
                success: false,
                message: `搜狗搜索时出错: ${error.message}`
            };
        }
    }
    /**
     * 使用夸克搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_quark(params) {
        // 定义参数类型
        const paramTypes = {
            page: 'number',
        };
        // 转换参数类型
        const convertedParams = convertParamTypes(params, paramTypes);
        const { query, page = 1 } = convertedParams;
        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }
        try {
            // 构建夸克搜索URL
            const encodedQuery = encodeURIComponent(query);
            const url = `https://quark.sm.cn/s?q=${encodedQuery}&page=${page}`;
            // 访问搜索页面
            const response = await Tools.Net.visit(url);
            if (!response || !response.content) {
                return {
                    success: false,
                    message: `无法获取夸克搜索结果`
                };
            }
            // 返回搜索结果
            return {
                success: true,
                message: `成功从夸克获取搜索结果`,
                query: query,
                page: page,
                content: response.content
            };
        }
        catch (error) {
            return {
                success: false,
                message: `夸克搜索时出错: ${error.message}`
            };
        }
    }
    /**
     * 在多个平台同时执行搜索，调用对应平台的搜索函数
     * @param {Object} params - 包含搜索查询和平台列表的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function combined_search(params) {
        const { query, platforms } = params;
        if (!query || query.trim() === "") {
            return {
                success: false,
                message: "请提供有效的搜索查询"
            };
        }
        if (!platforms || platforms.trim() === "") {
            return {
                success: false,
                message: "请提供有效的平台列表"
            };
        }
        const validPlatforms = [];
        const errors = [];
        // 验证平台
        for (const platform of platforms.split(",")) {
            if (["bing", "baidu", "sogou", "quark"].includes(platform)) {
                validPlatforms.push(platform);
            }
            else {
                errors.push({
                    platform,
                    error: `不支持的搜索平台: ${platform}`
                });
            }
        }
        // 如果没有有效平台，返回错误
        if (validPlatforms.length === 0) {
            return {
                success: false,
                message: "没有提供有效的搜索平台",
                supported_platforms: ["bing", "baidu", "sogou", "quark"]
            };
        }
        // 选择第一个有效平台执行搜索
        const platform = validPlatforms[0];
        // 调用对应平台的搜索函数
        try {
            let result;
            switch (platform) {
                case 'bing':
                    result = await search_bing({ query });
                    break;
                case 'baidu':
                    result = await search_baidu({ query, page: 1 });
                    break;
                case 'sogou':
                    result = await search_sogou({ query, page: 1 });
                    break;
                case 'quark':
                    result = await search_quark({ query, page: 1 });
                    break;
            }
            // 添加平台信息到结果中
            if (result) {
                result.platform = platform;
            }
            return result;
        }
        catch (error) {
            return {
                success: false,
                message: `${platform}搜索时出错: ${error.message}`,
                platform: platform
            };
        }
    }
    return {
        search_bing: async (params) => {
            await search_wrap(search_bing, params);
        },
        search_baidu: async (params) => {
            await search_wrap(search_baidu, params);
        },
        search_sogou: async (params) => {
            await search_wrap(search_sogou, params);
        },
        search_quark: async (params) => {
            await search_wrap(search_quark, params);
        },
        combined_search: async (params) => {
            await search_wrap(combined_search, params);
        }
    };
})();
// 导出所有函数
exports.search_bing = various_search.search_bing;
exports.search_baidu = various_search.search_baidu;
exports.search_sogou = various_search.search_sogou;
exports.search_quark = various_search.search_quark;
exports.combined_search = various_search.combined_search;
/**
 * 测试所有搜索函数的主函数
 */
async function main() {
    console.log("开始测试各搜索平台...");
    const testQuery = "人工智能最新发展";
    const results = {};
    // 测试函数包装器
    const testFunction = async (name, func, params) => {
        console.log(`测试 ${name} 搜索...`);
        try {
            // 存储原始complete函数
            const originalComplete = complete;
            // 创建一个Promise用于接收结果
            const result = await new Promise((resolve) => {
                // 替换complete函数
                // @ts-ignore 忽略类型检查
                complete = (result) => {
                    // 恢复原始complete函数
                    // @ts-ignore 忽略类型检查
                    complete = originalComplete;
                    resolve(result);
                };
                // 调用搜索函数
                func(params);
            });
            // 检查结果
            const success = result && result.success === true && (result.content || (result.data && result.data.content));
            results[name] = success;
            console.log(`${name} 搜索${success ? '成功' : '失败'}`);
            // 显示结果信息
            if (success) {
                const content = result.content || (result.data && result.data.content);
                const contentLength = content ? content.length : 0;
                console.log(`  - 获取到内容长度: ${contentLength} 字符`);
                // 显示内容片段
                if (contentLength > 0) {
                    const snippet = content.substring(0, 100).replace(/\n/g, ' ') + '...';
                    console.log(`  - 内容片段: ${snippet}`);
                }
            }
            else {
                console.log(`  - 失败原因: ${result.message || '未知错误'}`);
            }
        }
        catch (error) {
            results[name] = false;
            console.log(`${name} 搜索出现异常: ${error.message}`);
        }
    };
    // 测试各平台
    await testFunction("必应", various_search.search_bing, { query: testQuery });
    await testFunction("百度", various_search.search_baidu, { query: testQuery });
    await testFunction("搜狗", various_search.search_sogou, { query: testQuery });
    await testFunction("夸克", various_search.search_quark, { query: testQuery });
    // 测试组合搜索
    await testFunction("组合搜索", various_search.combined_search, {
        query: testQuery,
        platforms: ["bing", "baidu"]
    });
    // 输出测试结果汇总
    console.log("\n=== 测试结果汇总 ===");
    for (const [name, success] of Object.entries(results)) {
        console.log(`${name}: ${success ? '✅ 成功' : '❌ 失败'}`);
    }
    const successCount = Object.values(results).filter(v => v).length;
    const totalCount = Object.keys(results).length;
    console.log(`\n总计: ${successCount}/${totalCount} 成功率: ${Math.round(successCount / totalCount * 100)}%`);
}
// 导出main函数
exports.main = main;
