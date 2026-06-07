package org.ndroi.easy163.vpn.hookhttp;

/**
 * HTTP 钩子抽象基类，定义请求/响应拦截的规则匹配与处理接口。
 * 子类通过实现 {@link #rule} 方法判断是否匹配，可选覆盖
 * {@link #hookRequest} 和 {@link #hookResponse} 修改请求/响应内容。
 *
 * @author ndroi
 */
abstract public class Hook
{
    /**
     * 判断当前请求是否匹配此钩子的拦截规则
     *
     * @param request 待检查的 HTTP 请求
     * @return 如果请求匹配此钩子的规则则返回 true，否则返回 false
     */
    public abstract boolean rule(Request request);

    /**
     * 对匹配的请求进行修改。子类可覆盖此方法以修改请求头或请求体。
     * 默认实现为空操作。
     *
     * @param request 待修改的 HTTP 请求
     */
    public void hookRequest(Request request)
    {

    }

    /**
     * 对匹配的响应进行修改。子类可覆盖此方法以修改响应头或响应体。
     * 默认实现为空操作。
     *
     * @param response 待修改的 HTTP 响应
     */
    public void hookResponse(Response response)
    {

    }

    /**
     * 从请求 URI 中提取路径部分（去除查询参数）
     *
     * @param request HTTP 请求对象
     * @return 请求 URI 的路径部分，不包含查询参数（? 及之后的内容）
     */
    protected String getPath(Request request)
    {
        String path = request.getUri();
        int p = path.indexOf("?");
        if (p != -1)
        {
            path = path.substring(0, p);
        }
        return path;
    }
}
