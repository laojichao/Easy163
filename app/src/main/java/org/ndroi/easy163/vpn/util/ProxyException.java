package org.ndroi.easy163.vpn.util;

/**
 * 代理异常，表示 VPN 代理过程中的运行时错误
 *
 * @author ndroi
 */
public class ProxyException extends RuntimeException
{
    /**
     * 构造代理异常
     *
     * @param msg 异常描述信息
     */
    public ProxyException(String msg)
    {
        super(msg);
    }
}
