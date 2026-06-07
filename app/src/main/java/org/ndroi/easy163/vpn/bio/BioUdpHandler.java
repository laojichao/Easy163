package org.ndroi.easy163.vpn.bio;

import android.net.VpnService;
import android.util.Log;

import org.ndroi.easy163.vpn.config.Config;
import org.ndroi.easy163.vpn.tcpip.IpUtil;
import org.ndroi.easy163.vpn.tcpip.Packet;
import org.ndroi.easy163.vpn.util.ByteBufferPool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP 流量处理器，管理 UDP 数据报的转发，使用 Selector 实现非阻塞多路复用。
 * 通过阻塞队列接收 UDP 包并转发至远程服务器，下行数据通过 UdpDownWorker 线程回传设备。
 *
 * @author ndroi
 */
public class BioUdpHandler implements Runnable
{

    BlockingQueue<Packet> queue;

    BlockingQueue<ByteBuffer> networkToDeviceQueue;
    VpnService vpnService;

    private Selector selector;
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;

    /**
     * 下行 UDP 数据处理线程，通过 Selector 监听远程 DatagramChannel 的可读事件，
     * 读取响应数据并通过 sendUdpPack 回传给设备。
     */
    private static class UdpDownWorker implements Runnable
    {

        BlockingQueue<ByteBuffer> networkToDeviceQueue;
        BlockingQueue<UdpTunnel> tunnelQueue;
        Selector selector;

        private static AtomicInteger ipId = new AtomicInteger();

        /**
         * 构建并发送 UDP 响应包，将远程服务器的响应数据封装为 IP/UDP 包发回设备
         *
         * @param tunnel UDP 隧道
         * @param source 源地址（本地地址）
         * @param data   响应数据
         * @throws IOException 写入网络设备队列失败时抛出
         */
        private void sendUdpPack(UdpTunnel tunnel, InetSocketAddress source, byte[] data) throws IOException
        {
            int dataLen = 0;
            if (data != null)
            {
                dataLen = data.length;
            }
            Packet packet = IpUtil.buildUdpPacket(tunnel.remote, tunnel.local, ipId.addAndGet(1));

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
            packet.updateUDPBuffer(byteBuffer, dataLen);
            byteBuffer.position(HEADER_SIZE + dataLen);
            this.networkToDeviceQueue.offer(byteBuffer);
        }


        /**
         * 构造下行 UDP 数据处理线程
         *
         * @param selector            Selector 实例，用于非阻塞多路复用
         * @param networkToDeviceQueue 网络响应写回设备的阻塞队列
         * @param tunnelQueue         新建隧道注册队列
         */
        public UdpDownWorker(Selector selector, BlockingQueue<ByteBuffer> networkToDeviceQueue, BlockingQueue<UdpTunnel> tunnelQueue)
        {
            this.networkToDeviceQueue = networkToDeviceQueue;
            this.tunnelQueue = tunnelQueue;
            this.selector = selector;
        }

        @Override
        public void run()
        {
            try
            {
                while (true)
                {
                    int readyChannels = selector.select();
                    while (true)
                    {
                        UdpTunnel tunnel = tunnelQueue.poll();
                        if (tunnel == null)
                        {
                            break;
                        } else
                        {
                            try
                            {
                                SelectionKey key = tunnel.channel.register(selector, SelectionKey.OP_READ, tunnel);
                                key.interestOps(SelectionKey.OP_READ);
                                boolean isvalid = key.isValid();
                            } catch (IOException e)
                            {
                                Log.d(TAG, "register fail", e);
                            }
                        }
                    }
                    if (readyChannels == 0)
                    {
                        selector.selectedKeys().clear();
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext())
                    {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        if (key.isValid() && key.isReadable())
                        {
                            try
                            {
                                DatagramChannel inputChannel = (DatagramChannel) key.channel();

                                ByteBuffer receiveBuffer = ByteBufferPool.acquire();
                                int readBytes = inputChannel.read(receiveBuffer);
                                receiveBuffer.flip();
                                byte[] data = new byte[receiveBuffer.remaining()];
                                receiveBuffer.get(data);
                                sendUdpPack((UdpTunnel) key.attachment(), (InetSocketAddress) inputChannel.getLocalAddress(), data);
                            } catch (IOException e)
                            {
                                Log.e(TAG, "error", e);
                            }

                        }
                    }
                }
            } catch (Exception e)
            {
                Log.e(TAG, "error", e);
                System.exit(0);
            } finally
            {
                //Log.d(TAG, "BioUdpHandler quit");
            }


        }
    }

    /**
     * 构造 UDP 流量处理器
     *
     * @param queue               设备发出的 UDP 包阻塞队列
     * @param networkToDeviceQueue 网络响应写回设备的阻塞队列
     * @param vpnService          VPN 服务实例，用于 socket 保护
     */
    public BioUdpHandler(BlockingQueue<Packet> queue, BlockingQueue<ByteBuffer> networkToDeviceQueue, VpnService vpnService)
    {
        this.queue = queue;
        this.networkToDeviceQueue = networkToDeviceQueue;
        this.vpnService = vpnService;
    }

    private static final String TAG = BioUdpHandler.class.getSimpleName();


    Map<String, DatagramChannel> udpSockets = new HashMap();


    /**
     * UDP 隧道封装，包含本地/远程地址和 DatagramChannel
     */
    private static class UdpTunnel
    {
        InetSocketAddress local;
        InetSocketAddress remote;
        DatagramChannel channel;

    }

    /**
     * 主循环，持续从队列取 UDP 包并转发到远程服务器。
     * 首次通信时创建 DatagramChannel 并注册到 Selector 用于下行监听。
     */
    @Override
    public void run()
    {
        try
        {
            BlockingQueue<UdpTunnel> tunnelQueue = new ArrayBlockingQueue<>(100);
            selector = Selector.open();
            Thread t = new Thread(new UdpDownWorker(selector, networkToDeviceQueue, tunnelQueue));
            t.start();


            while (true)
            {
                Packet packet = queue.take();

                InetAddress destinationAddress = packet.ip4Header.destinationAddress;
                Packet.UDPHeader header = packet.udpHeader;

                //Log.d(TAG, String.format("get pack %d udp %d ", packet.packId, header.length));

                int destinationPort = header.destinationPort;
                int sourcePort = header.sourcePort;
                String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                if (!udpSockets.containsKey(ipAndPort))
                {
                    DatagramChannel outputChannel = DatagramChannel.open();
                    vpnService.protect(outputChannel.socket());
                    outputChannel.socket().bind(null);
                    outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

                    outputChannel.configureBlocking(false);

                    UdpTunnel tunnel = new UdpTunnel();
                    tunnel.local = new InetSocketAddress(packet.ip4Header.sourceAddress, header.sourcePort);
                    tunnel.remote = new InetSocketAddress(packet.ip4Header.destinationAddress, header.destinationPort);
                    tunnel.channel = outputChannel;
                    tunnelQueue.offer(tunnel);

                    selector.wakeup();

                    udpSockets.put(ipAndPort, outputChannel);
                }

                DatagramChannel outputChannel = udpSockets.get(ipAndPort);
                ByteBuffer buffer = packet.backingBuffer;
                try
                {
                    while (packet.backingBuffer.hasRemaining())
                    {
                        int w = outputChannel.write(buffer);
                        if (Config.logRW)
                        {
                            //Log.d(TAG, String.format("write udp pack %d len %d %s ", packet.packId, w, ipAndPort));
                        }

                    }
                } catch (IOException e)
                {
                    Log.e(TAG, "udp write error", e);
                    outputChannel.close();
                    udpSockets.remove(ipAndPort);
                }
            }
        } catch (Exception e)
        {
            Log.e(TAG, "error", e);
            //System.exit(0);
        }
    }
}
