package org.ndroi.easy163.vpn.bio;

import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import org.ndroi.easy163.vpn.hookhttp.HookHttp;
import org.ndroi.easy163.vpn.tcpip.IpUtil;
import org.ndroi.easy163.vpn.tcpip.Packet;
import org.ndroi.easy163.vpn.tcpip.TCBStatus;
import org.ndroi.easy163.vpn.util.ByteBufferPool;
import org.ndroi.easy163.vpn.util.ProxyException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP 流量处理器，管理 TCP 隧道的完整生命周期（三次握手、数据传输、四次挥手），
 * 支持 HTTP 请求/响应拦截。通过阻塞队列接收 IP 包并分发到对应隧道处理。
 *
 * @author ndroi
 */
public class BioTcpHandler implements Runnable
{
    BlockingQueue<Packet> queue;

    ConcurrentHashMap<String, TcpTunnel> tunnels = new ConcurrentHashMap();

    private static int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    /**
     * TCP 隧道状态封装，包含序列号、确认号、连接状态、socket 通道等。
     * 每条隧道维护独立的双向数据通道和 TCP 状态机。
     */
    public static class TcpTunnel
    {
        static AtomicInteger tunnelIds = new AtomicInteger(0);
        public final int tunnelId = tunnelIds.addAndGet(1);

        public long mySequenceNum = 0;
        public long theirSequenceNum = 0;
        public long myAcknowledgementNum = 0;
        public long theirAcknowledgementNum = 0;

        public TCBStatus tcbStatus = TCBStatus.SYN_SENT;
        public BlockingQueue<Packet> tunnelInputQueue = new ArrayBlockingQueue<Packet>(1024);
        public InetSocketAddress sourceAddress;
        public InetSocketAddress destinationAddress;
        public SocketChannel destSocket;
        private VpnService vpnService;
        BlockingQueue<ByteBuffer> networkToDeviceQueue;

        public int packId = 1;

        public boolean upActive = true;
        public boolean downActive = true;
        public String tunnelKey;
        public BlockingQueue<String> tunnelCloseMsgQueue;
    }

    private static final String TAG = BioTcpHandler.class.getSimpleName();

    private VpnService vpnService;
    BlockingQueue<ByteBuffer> networkToDeviceQueue;

    /**
     * 构造 TCP 流量处理器
     *
     * @param queue               设备发出的 TCP 包阻塞队列
     * @param networkToDeviceQueue 网络响应写回设备的阻塞队列
     * @param vpnService          VPN 服务实例，用于 socket 保护
     */
    public BioTcpHandler(BlockingQueue<Packet> queue, BlockingQueue<ByteBuffer> networkToDeviceQueue, VpnService vpnService)
    {
        this.queue = queue;
        this.vpnService = vpnService;
        this.networkToDeviceQueue = networkToDeviceQueue;
    }

    /**
     * 将大数据分片发送为多个 TCP 包，每片大小不超过缓冲区最大容量
     *
     * @param tunnel 目标 TCP 隧道
     * @param flag   TCP 标志位
     * @param data   待发送的数据
     */
    private static void sendMultiPack(TcpTunnel tunnel, byte flag, byte[] data)
    {
        int unitSize = ByteBufferPool.BUFFER_SIZE - HEADER_SIZE;
        int offset = 0;
        while (offset < data.length)
        {
            int len = (offset + unitSize > data.length ? data.length - offset : unitSize);
            byte[] unit = new byte[len];
            System.arraycopy(data, offset, unit, 0, len);
            sendTcpPack(tunnel, flag, unit);
            offset += len;
        }
    }

    /**
     * 构建并发送单个 TCP 包，更新隧道序列号。
     * SYN 和 FIN 标志使序列号加 1，ACK 标志使序列号增加数据长度。
     *
     * @param tunnel 目标 TCP 隧道
     * @param flag   TCP 标志位（SYN/ACK/FIN/RST 的组合）
     * @param data   待发送的数据，可为 null
     */
    private static void sendTcpPack(TcpTunnel tunnel, byte flag, byte[] data)
    {

        int dataLen = 0;
        if (data != null)
        {
            dataLen = data.length;
        }
        Packet packet = IpUtil.buildTcpPacket(tunnel.destinationAddress, tunnel.sourceAddress, flag,
                tunnel.myAcknowledgementNum, tunnel.mySequenceNum, tunnel.packId);
        tunnel.packId += 1;
        ByteBuffer byteBuffer = ByteBufferPool.acquire();
        //
        byteBuffer.position(HEADER_SIZE);
        if (data != null)
        {
            if (byteBuffer.remaining() < data.length)
            {
                System.currentTimeMillis();
            }
            byteBuffer.put(data);
        }

        packet.updateTCPBuffer(byteBuffer, flag, tunnel.mySequenceNum, tunnel.myAcknowledgementNum, dataLen);
        byteBuffer.position(HEADER_SIZE + dataLen);

        tunnel.networkToDeviceQueue.offer(byteBuffer);

        if ((flag & (byte) Packet.TCPHeader.SYN) != 0)
        {
            tunnel.mySequenceNum += 1;
        }
        if ((flag & (byte) Packet.TCPHeader.FIN) != 0)
        {
            tunnel.mySequenceNum += 1;
        }
        if ((flag & (byte) Packet.TCPHeader.ACK) != 0)
        {
            tunnel.mySequenceNum += dataLen;
        }
    }

    /**
     * 上行数据处理线程，处理来自设备的 SYN/ACK/FIN/RST 包，
     * 完成三次握手后连接远程服务器，将数据转发到远程。
     */
    private static class UpStreamWorker implements Runnable
    {

        TcpTunnel tunnel;

        /**
         * 构造上行数据处理线程
         *
         * @param tunnel 关联的 TCP 隧道
         */
        public UpStreamWorker(TcpTunnel tunnel)
        {
            this.tunnel = tunnel;
        }

        private void startDownStream()
        {
            Thread t = new Thread(new DownStreamWorker(tunnel));
            t.start();
        }

        private void connectRemote()
        {
            try
            {
                //connect
                SocketChannel remote = SocketChannel.open();
                //tunnel.vpnService.protect(remote.socket());
                InetSocketAddress address = tunnel.destinationAddress;

                Long ts = System.currentTimeMillis();
                remote.socket().connect(address, 10 * 1000);
                remote.socket().setKeepAlive(true);

                Long te = System.currentTimeMillis();
                Log.i(TAG, String.format("connectRemote %d cost %d  remote %s", tunnel.tunnelId, te - ts, tunnel.destinationAddress.toString()));
                tunnel.destSocket = remote;

                startDownStream();
            } catch (Exception e)
            {
                Log.e(TAG, e.getMessage(), e);
                throw new ProxyException("connectRemote fail" + tunnel.destinationAddress.toString());
            }
        }

        int synCount = 0;

        private void handleSyn(Packet packet)
        {

            if (tunnel.tcbStatus == TCBStatus.SYN_SENT)
            {
                tunnel.tcbStatus = TCBStatus.SYN_RECEIVED;
            }
            Packet.TCPHeader tcpHeader = packet.tcpHeader;
            if (synCount == 0)
            {
                tunnel.mySequenceNum = 1;
                tunnel.theirSequenceNum = tcpHeader.sequenceNumber;
                tunnel.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
                tunnel.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
                if (tunnel.destinationAddress.getPort() == 443)
                {
                    sendTcpPack(tunnel, (byte) (Packet.TCPHeader.ACK | Packet.TCPHeader.RST), null);
                    tunnel.tunnelCloseMsgQueue.add(tunnel.tunnelKey);
                } else
                {
                    sendTcpPack(tunnel, (byte) (Packet.TCPHeader.SYN | Packet.TCPHeader.ACK), null);
                }
            } else
            {
                tunnel.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
            }
            synCount += 1;
        }

        private void writeToRemote(ByteBuffer buffer) throws IOException
        {
            if (tunnel.upActive)
            {
                if (tunnel.destinationAddress.getPort() == 80)
                {
                    buffer = HookHttp.getInstance().checkAndHookRequest(tunnel, buffer);
                }
                if(tunnel.destSocket != null) // I don't know why need this
                {
                    tunnel.destSocket.write(buffer);
                }
            }
        }

        private void handleAck(Packet packet) throws IOException
        {

            if (tunnel.tcbStatus == TCBStatus.SYN_RECEIVED)
            {
                tunnel.tcbStatus = TCBStatus.ESTABLISHED;
            }

            Packet.TCPHeader tcpHeader = packet.tcpHeader;
            int payloadSize = packet.backingBuffer.remaining();

            if (payloadSize == 0)
            {
                return;
            }

            long newAck = tcpHeader.sequenceNumber + payloadSize;
            if (newAck <= tunnel.myAcknowledgementNum)
            {
                return;
            }
            tunnel.myAcknowledgementNum = tcpHeader.sequenceNumber;
            tunnel.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
            tunnel.myAcknowledgementNum += payloadSize;
            writeToRemote(packet.backingBuffer);
            sendTcpPack(tunnel, (byte) Packet.TCPHeader.ACK, null);
        }

        private void handleFin(Packet packet)
        {
            tunnel.myAcknowledgementNum = packet.tcpHeader.sequenceNumber + 1;
            tunnel.theirAcknowledgementNum = packet.tcpHeader.acknowledgementNumber;
            sendTcpPack(tunnel, (byte) (Packet.TCPHeader.ACK), null);
            closeUpStream(tunnel);
            tunnel.tcbStatus = TCBStatus.CLOSE_WAIT;
        }

        private void handleRst(Packet packet)
        {
            try
            {
                synchronized (tunnel)
                {
                    if (tunnel.destSocket != null)
                    {
                        tunnel.destSocket.close();
                    }

                }
            } catch (IOException e)
            {
                Log.e(TAG, "close error", e);
            }

            synchronized (tunnel)
            {
                tunnel.upActive = false;
                tunnel.downActive = false;
                tunnel.tcbStatus = TCBStatus.CLOSE_WAIT;
            }
        }

        private void loop()
        {
            while (true)
            {
                Packet packet = null;
                try
                {
                    packet = tunnel.tunnelInputQueue.take();
                    synchronized (tunnel)
                    {
                        boolean end = false;
                        Packet.TCPHeader tcpHeader = packet.tcpHeader;

                        if (tcpHeader.isSYN())
                        {
                            handleSyn(packet);
                            end = true;
                        }
                        if (!end && tcpHeader.isRST())
                        {
                            handleRst(packet);
                            break;
                        }
                        if (!end && tcpHeader.isFIN())
                        {
                            handleFin(packet);
                            end = true;
                        }
                        if (!end && tcpHeader.isACK())
                        {
                            handleAck(packet);
                        }
                    }
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return;
                }
            }
        }

        @Override
        public void run()
        {
            try
            {
                if (tunnel.destinationAddress.getPort() != 443)
                {
                    connectRemote();
                }
                loop();
            } catch (ProxyException e)
            {
                e.printStackTrace();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查隧道是否已完全关闭（上行和下行通道均不活跃）
     *
     * @param tunnel 待检查的 TCP 隧道
     * @return 如果隧道上下行均关闭则返回 true
     */
    public static boolean isClosedTunnel(TcpTunnel tunnel)
    {
        return !tunnel.upActive && !tunnel.downActive;
    }

    /**
     * 关闭下行通道并发送 FIN，若上下行均关闭则将隧道加入关闭队列
     *
     * @param tunnel 目标 TCP 隧道
     */
    private static void closeDownStream(TcpTunnel tunnel)
    {
        synchronized (tunnel)
        {
            try
            {
                if (tunnel.destSocket != null && tunnel.destSocket.isOpen())
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    {
                        tunnel.destSocket.shutdownInput();
                    } else
                    {
                        tunnel.destSocket.close();
                        tunnel.destSocket = null;
                    }
                }
            } catch (Exception e)
            {
                e.printStackTrace();
            }
            sendTcpPack(tunnel, (byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK), null);
            tunnel.downActive = false;
            if (isClosedTunnel(tunnel))
            {
                tunnel.tunnelCloseMsgQueue.add(tunnel.tunnelKey);
            }
        }
    }

    /**
     * 关闭上行通道，shutdown 输出流或关闭 socket
     *
     * @param tunnel 目标 TCP 隧道
     */
    private static void closeUpStream(TcpTunnel tunnel)
    {
        synchronized (tunnel)
        {
            try
            {
                if (tunnel.destSocket != null && tunnel.destSocket.isOpen())
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    {
                        tunnel.destSocket.shutdownOutput();
                    } else
                    {
                        tunnel.destSocket.close();
                        tunnel.destSocket = null;
                    }
                }

            } catch (Exception e)
            {
                e.printStackTrace();
            }
            tunnel.upActive = false;
        }
    }

    /**
     * 通过 RST 强制关闭隧道，关闭 socket 并发送 RST 包
     *
     * @param tunnel 目标 TCP 隧道
     */
    private static void closeRst(TcpTunnel tunnel)
    {
        synchronized (tunnel)
        {
            try
            {
                if (tunnel.destSocket != null && tunnel.destSocket.isOpen())
                {
                    tunnel.destSocket.close();
                    tunnel.destSocket = null;
                }
            } catch (Exception e)
            {
                e.printStackTrace();
            }
            sendTcpPack(tunnel, (byte) Packet.TCPHeader.RST, null);
            tunnel.upActive = false;
            tunnel.downActive = false;
            tunnel.tunnelCloseMsgQueue.add(tunnel.tunnelKey);
        }
    }

    /**
     * 下行数据处理线程，从远程服务器读取响应数据，
     * 经过 HookHttp 拦截处理后回传给设备。
     * 正常关闭时发送 FIN，异常关闭时发送 RST。
     */
    private static class DownStreamWorker implements Runnable
    {
        TcpTunnel tunnel;

        /**
         * 构造下行数据处理线程
         *
         * @param tunnel 关联的 TCP 隧道
         */
        public DownStreamWorker(TcpTunnel tunnel)
        {
            this.tunnel = tunnel;
        }

        @Override
        public void run()
        {
            String quitType = "rst";
            try
            {
                while (true)
                {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 1024);
                    if (tunnel.destSocket == null)
                    {
                        throw new ProxyException("tunnel maybe closed");
                    }

                    int n = BioUtil.read(tunnel.destSocket, buffer);

                    synchronized (tunnel)
                    {
                        if (n == -1)
                        {
                            quitType = "fin";
                            break;
                        } else if (n == 0)
                        {
                            Thread.sleep(50);
                        } else
                        {
                            if (tunnel.tcbStatus != TCBStatus.CLOSE_WAIT)
                            {
                                buffer.flip();
                                buffer = HookHttp.getInstance().checkAndHookResponse(tunnel, buffer);
                                byte[] data = new byte[buffer.remaining()];
                                buffer.get(data);
                                sendMultiPack(tunnel, (byte) (Packet.TCPHeader.ACK), data);
                            }
                        }
                    }
                }
            } catch (ClosedChannelException e)
            {
                Log.w(TAG, String.format("channel closed %s", e.getMessage()));
                quitType = "rst";
            } catch (IOException e)
            {
                Log.e(TAG, e.getMessage(), e);
                quitType = "rst";
            } catch (Exception e)
            {
                quitType = "rst";
                Log.e(TAG, "DownStreamWorker fail", e);
            }
            synchronized (tunnel)
            {
                if (quitType.equals("fin"))
                {
                    closeDownStream(tunnel);
                } else if (quitType.equals("rst"))
                {
                    closeRst(tunnel);
                }
            }
        }
    }

    /**
     * 初始化新的 TCP 隧道，设置源/目标地址，启动上行处理线程
     *
     * @param packet 触发隧道创建的 SYN 包
     * @return 新创建的 TCP 隧道实例
     */
    private TcpTunnel initTunnel(Packet packet)
    {
        TcpTunnel tunnel = new TcpTunnel();
        tunnel.sourceAddress = new InetSocketAddress(packet.ip4Header.sourceAddress, packet.tcpHeader.sourcePort);
        tunnel.destinationAddress = new InetSocketAddress(packet.ip4Header.destinationAddress, packet.tcpHeader.destinationPort);
        tunnel.vpnService = vpnService;
        tunnel.networkToDeviceQueue = networkToDeviceQueue;
        tunnel.tunnelCloseMsgQueue = tunnelCloseMsgQueue;
        Thread t = new Thread(new UpStreamWorker(tunnel));
        t.start();
        return tunnel;
    }

    /** 隧道关闭消息队列，用于异步通知主循环清理已关闭的隧道 */
    public BlockingQueue<String> tunnelCloseMsgQueue = new ArrayBlockingQueue<>(1024);

    /**
     * 主循环，持续从队列取包并分发到对应隧道。
     * 同时处理隧道关闭消息，清理已断开的隧道。
     */
    @Override
    public void run()
    {

        while (true)
        {
            try
            {
                Packet currentPacket = queue.take();
                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;
                Packet.TCPHeader tcpHeader = currentPacket.tcpHeader;
                int destinationPort = tcpHeader.destinationPort;
                int sourcePort = tcpHeader.sourcePort;
                String ipAndPort = destinationAddress.getHostAddress() + ":" +
                        destinationPort + ":" + sourcePort;

                while (true)
                {
                    String s = this.tunnelCloseMsgQueue.poll();
                    if (s == null)
                    {
                        break;
                    } else
                    {
                        tunnels.remove(s);
                    }
                }

                TcpTunnel tcpTunnel = tunnels.get(ipAndPort);
                if (tcpTunnel == null)
                {
                    tcpTunnel = initTunnel(currentPacket);
                    tcpTunnel.tunnelKey = ipAndPort;
                    tunnels.put(ipAndPort, tcpTunnel);
                }
                tcpTunnel.tunnelInputQueue.offer(currentPacket);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
