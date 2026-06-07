package org.ndroi.easy163.vpn.tcpip;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 数据包表示，解析和构建 IPv4 数据包，支持 TCP 和 UDP 协议头部
 *
 * @author ndroi
 */
// TODO: Reduce public mutability
public class Packet
{
    public static final int IP4_HEADER_SIZE = 20;
    public static final int TCP_HEADER_SIZE = 20;
    public static final int UDP_HEADER_SIZE = 8;


    private static AtomicInteger globalPackId = new AtomicInteger();
    public int packId = globalPackId.addAndGet(1);
    public IP4Header ip4Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer backingBuffer;

    public boolean isTCP;
    public boolean isUDP;

    /**
     * 默认构造函数
     */
    public Packet()
    {

    }

    /**
     * 从原始字节缓冲区解析数据包
     *
     * @param buffer 包含 IP 数据包原始字节的缓冲区
     * @throws UnknownHostException 当解析的 IP 地址格式无效时抛出
     */
    public Packet(ByteBuffer buffer) throws UnknownHostException
    {
        this.ip4Header = new IP4Header(buffer);
        if (this.ip4Header.protocol == IP4Header.TransportProtocol.TCP)
        {
            this.tcpHeader = new TCPHeader(buffer);
            this.isTCP = true;
        } else if (ip4Header.protocol == IP4Header.TransportProtocol.UDP)
        {
            this.udpHeader = new UDPHeader(buffer);
            this.isUDP = true;
        }
        this.backingBuffer = buffer;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder("Packet{");
        sb.append("ip4Header=").append(ip4Header);
        if (isTCP) sb.append(", tcpHeader=").append(tcpHeader);
        else if (isUDP) sb.append(", udpHeader=").append(udpHeader);
        sb.append(", payloadSize=").append(backingBuffer.limit() - backingBuffer.position());
        sb.append('}');
        return sb.toString();
    }

    /**
     * 判断是否为 TCP 数据包
     *
     * @return 是 TCP 数据包返回 true
     */
    public boolean isTCP()
    {
        return isTCP;
    }

    /**
     * 判断是否为 UDP 数据包
     *
     * @return 是 UDP 数据包返回 true
     */
    public boolean isUDP()
    {
        return isUDP;
    }


    /**
     * 更新 TCP 包的头部字段和校验和
     *
     * @param buffer      目标字节缓冲区
     * @param flags       TCP 标志位
     * @param sequenceNum 序列号
     * @param ackNum      确认号
     * @param payloadSize 载荷大小（字节）
     */
    public void updateTCPBuffer(ByteBuffer buffer, byte flags, long sequenceNum, long ackNum, int payloadSize)
    {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        tcpHeader.flags = flags;
        backingBuffer.put(IP4_HEADER_SIZE + 13, flags);

        tcpHeader.sequenceNumber = sequenceNum;
        backingBuffer.putInt(IP4_HEADER_SIZE + 4, (int) sequenceNum);

        tcpHeader.acknowledgementNumber = ackNum;
        backingBuffer.putInt(IP4_HEADER_SIZE + 8, (int) ackNum);

        // Reset header size, since we don't need options
        byte dataOffset = (byte) (TCP_HEADER_SIZE << 2);
        tcpHeader.dataOffsetAndReserved = dataOffset;
        backingBuffer.put(IP4_HEADER_SIZE + 12, dataOffset);

        updateTCPChecksum(payloadSize);

        int ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    /**
     * 更新 UDP 包的头部字段和校验和
     *
     * @param buffer      目标字节缓冲区
     * @param payloadSize 载荷大小（字节）
     */
    public void updateUDPBuffer(ByteBuffer buffer, int payloadSize)
    {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        int udpTotalLength = UDP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(IP4_HEADER_SIZE + 4, (short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        // Disable UDP checksum validation
        backingBuffer.putShort(IP4_HEADER_SIZE + 6, (short) 0);
        udpHeader.checksum = 0;

        int ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    /**
     * 计算并更新 IPv4 头部校验和
     */
    private void updateIP4Checksum()
    {
        ByteBuffer buffer = backingBuffer.duplicate();
        buffer.position(0);

        // Clear previous checksum
        buffer.putShort(10, (short) 0);

        int ipLength = ip4Header.headerLength;
        int sum = 0;
        while (ipLength > 0)
        {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            ipLength -= 2;
        }
        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        ip4Header.headerChecksum = sum;
        backingBuffer.putShort(10, (short) sum);
    }

    /**
     * 计算并更新 TCP 校验和（含伪头部）
     *
     * @param payloadSize 载荷大小（字节）
     */
    private void updateTCPChecksum(int payloadSize)
    {
        int sum = 0;
        int tcpLength = TCP_HEADER_SIZE + payloadSize;

        // Calculate pseudo-header checksum
        ByteBuffer buffer = ByteBuffer.wrap(ip4Header.sourceAddress.getAddress());
        sum = BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(ip4Header.destinationAddress.getAddress());
        sum += BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        sum += IP4Header.TransportProtocol.TCP.getNumber() + tcpLength;

        buffer = backingBuffer.duplicate();
        // Clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, (short) 0);

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE);
        while (tcpLength > 1)
        {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }
        if (tcpLength > 0)
            sum += BitUtils.getUnsignedByte(buffer.get()) << 8;

        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        tcpHeader.checksum = sum;
        backingBuffer.putShort(IP4_HEADER_SIZE + 16, (short) sum);
    }

    /**
     * 将头部字段写入缓冲区
     *
     * @param buffer 目标字节缓冲区
     */
    private void fillHeader(ByteBuffer buffer)
    {
        ip4Header.fillHeader(buffer);
        if (isUDP)
            udpHeader.fillHeader(buffer);
        else if (isTCP)
            tcpHeader.fillHeader(buffer);
    }

    /**
     * IPv4 头部，包含版本、TTL、协议、源/目标地址等字段
     *
     * @author ndroi
     */
    public static class IP4Header
    {
        public byte version;
        public byte IHL;
        public int headerLength;
        public short typeOfService;
        public int totalLength;

        public int identificationAndFlagsAndFragmentOffset;

        public short TTL;
        public short protocolNum;
        public TransportProtocol protocol;
        public int headerChecksum;

        public InetAddress sourceAddress;
        public InetAddress destinationAddress;

        public int optionsAndPadding;

        /**
         * 传输层协议类型枚举
         *
         * @author ndroi
         */
        public enum TransportProtocol
        {
            TCP(6),
            UDP(17),
            Other(0xFF);

            private int protocolNumber;

            TransportProtocol(int protocolNumber)
            {
                this.protocolNumber = protocolNumber;
            }

            /**
             * 根据协议号获取对应的枚举值
             *
             * @param protocolNumber 传输层协议号
             * @return 对应的传输协议枚举
             */
            private static TransportProtocol numberToEnum(int protocolNumber)
            {
                if (protocolNumber == 6)
                    return TCP;
                else if (protocolNumber == 17)
                    return UDP;
                else
                    return Other;
            }

            /**
             * 获取协议号
             *
             * @return 协议号数值
             */
            public int getNumber()
            {
                return this.protocolNumber;
            }
        }

        /**
         * 默认构造函数
         */
        public IP4Header()
        {

        }

        /**
         * 从字节缓冲区解析 IPv4 头部
         *
         * @param buffer 包含 IPv4 头部字节的缓冲区
         * @throws UnknownHostException 当解析的 IP 地址格式无效时抛出
         */
        private IP4Header(ByteBuffer buffer) throws UnknownHostException
        {
            byte versionAndIHL = buffer.get();
            this.version = (byte) (versionAndIHL >> 4);
            this.IHL = (byte) (versionAndIHL & 0x0F);
            this.headerLength = this.IHL << 2;

            this.typeOfService = BitUtils.getUnsignedByte(buffer.get());
            this.totalLength = BitUtils.getUnsignedShort(buffer.getShort());

            this.identificationAndFlagsAndFragmentOffset = buffer.getInt();

            this.TTL = BitUtils.getUnsignedByte(buffer.get());
            this.protocolNum = BitUtils.getUnsignedByte(buffer.get());
            this.protocol = TransportProtocol.numberToEnum(protocolNum);
            this.headerChecksum = BitUtils.getUnsignedShort(buffer.getShort());

            byte[] addressBytes = new byte[4];
            buffer.get(addressBytes, 0, 4);
            this.sourceAddress = InetAddress.getByAddress(addressBytes);

            buffer.get(addressBytes, 0, 4);
            this.destinationAddress = InetAddress.getByAddress(addressBytes);

            //this.optionsAndPadding = buffer.getInt();
        }

        /**
         * 将 IPv4 头部字段写入缓冲区
         *
         * @param buffer 目标字节缓冲区
         */
        public void fillHeader(ByteBuffer buffer)
        {
            buffer.put((byte) (this.version << 4 | this.IHL));
            buffer.put((byte) this.typeOfService);
            buffer.putShort((short) this.totalLength);

            buffer.putInt(this.identificationAndFlagsAndFragmentOffset);

            buffer.put((byte) this.TTL);
            buffer.put((byte) this.protocol.getNumber());
            buffer.putShort((short) this.headerChecksum);

            buffer.put(this.sourceAddress.getAddress());
            buffer.put(this.destinationAddress.getAddress());
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder("IP4Header{");
            sb.append("version=").append(version);
            sb.append(", IHL=").append(IHL);
            sb.append(", typeOfService=").append(typeOfService);
            sb.append(", totalLength=").append(totalLength);
            sb.append(", identificationAndFlagsAndFragmentOffset=").append(identificationAndFlagsAndFragmentOffset);
            sb.append(", TTL=").append(TTL);
            sb.append(", protocol=").append(protocolNum).append(":").append(protocol);
            sb.append(", headerChecksum=").append(headerChecksum);
            sb.append(", sourceAddress=").append(sourceAddress.getHostAddress());
            sb.append(", destinationAddress=").append(destinationAddress.getHostAddress());
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * TCP 头部，包含端口、序列号、标志位等字段
     *
     * @author ndroi
     */
    public static class TCPHeader
    {
        public static final int FIN = 0x01;
        public static final int SYN = 0x02;
        public static final int RST = 0x04;
        public static final int PSH = 0x08;
        public static final int ACK = 0x10;
        public static final int URG = 0x20;

        public int sourcePort;
        public int destinationPort;

        public long sequenceNumber;
        public long acknowledgementNumber;

        public byte dataOffsetAndReserved;
        public int headerLength;
        public byte flags;
        public int window;

        public int checksum;
        public int urgentPointer;

        public byte[] optionsAndPadding;

        /**
         * 从字节缓冲区解析 TCP 头部
         *
         * @param buffer 包含 TCP 头部字节的缓冲区
         */
        public TCPHeader(ByteBuffer buffer)
        {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.sequenceNumber = BitUtils.getUnsignedInt(buffer.getInt());
            this.acknowledgementNumber = BitUtils.getUnsignedInt(buffer.getInt());

            this.dataOffsetAndReserved = buffer.get();
            this.headerLength = (this.dataOffsetAndReserved & 0xF0) >> 2;
            this.flags = buffer.get();
            this.window = BitUtils.getUnsignedShort(buffer.getShort());

            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
            this.urgentPointer = BitUtils.getUnsignedShort(buffer.getShort());

            int optionsLength = this.headerLength - TCP_HEADER_SIZE;
            if (optionsLength > 0)
            {
                optionsAndPadding = new byte[optionsLength];
                buffer.get(optionsAndPadding, 0, optionsLength);
            }
        }

        /**
         * 默认构造函数
         */
        public TCPHeader()
        {

        }

        /**
         * 判断是否设置了 FIN 标志
         *
         * @return 设置了 FIN 返回 true
         */
        public boolean isFIN()
        {
            return (flags & FIN) == FIN;
        }

        /**
         * 判断是否设置了 SYN 标志
         *
         * @return 设置了 SYN 返回 true
         */
        public boolean isSYN()
        {
            return (flags & SYN) == SYN;
        }


        /**
         * 判断是否设置了 RST 标志
         *
         * @return 设置了 RST 返回 true
         */
        public boolean isRST()
        {
            return (flags & RST) == RST;
        }

        /**
         * 判断是否设置了 PSH 标志
         *
         * @return 设置了 PSH 返回 true
         */
        public boolean isPSH()
        {
            return (flags & PSH) == PSH;
        }

        /**
         * 判断是否设置了 ACK 标志
         *
         * @return 设置了 ACK 返回 true
         */
        public boolean isACK()
        {
            return (flags & ACK) == ACK;
        }

        /**
         * 判断是否设置了 URG 标志
         *
         * @return 设置了 URG 返回 true
         */
        public boolean isURG()
        {
            return (flags & URG) == URG;
        }

        private void fillHeader(ByteBuffer buffer)
        {
            buffer.putShort((short) sourcePort);
            buffer.putShort((short) destinationPort);

            buffer.putInt((int) sequenceNumber);
            buffer.putInt((int) acknowledgementNumber);

            buffer.put(dataOffsetAndReserved);
            buffer.put(flags);
            buffer.putShort((short) window);

            buffer.putShort((short) checksum);
            buffer.putShort((short) urgentPointer);
        }

        /**
         * 将标志位转换为可读字符串（如 "SYN ACK"）
         *
         * @param flags 标志位字节
         * @return 标志位的可读字符串表示
         */
        public static String flagToString(byte flags)
        {
            final StringBuilder sb = new StringBuilder("");
            if ((flags & FIN) == FIN) sb.append("FIN ");
            if ((flags & SYN) == SYN) sb.append("SYN ");
            if ((flags & RST) == RST) sb.append("RST ");
            if ((flags & PSH) == PSH) sb.append("PSH ");
            if ((flags & ACK) == ACK) sb.append("ACK ");
            if ((flags & URG) == URG) sb.append("URG ");
            return sb.toString();
        }

        /**
         * 输出简化的 TCP 头部信息（标志位、序列号和确认号）
         *
         * @return 简化的 TCP 头部描述字符串
         */
        public String printSimple()
        {
            final StringBuilder sb = new StringBuilder("");
            if (isFIN()) sb.append("FIN ");
            if (isSYN()) sb.append("SYN ");
            if (isRST()) sb.append("RST ");
            if (isPSH()) sb.append("PSH ");
            if (isACK()) sb.append("ACK ");
            if (isURG()) sb.append("URG ");
            sb.append("seq " + sequenceNumber + " ");
            sb.append("ack " + acknowledgementNumber + " ");
            return sb.toString();
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder("TCPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", sequenceNumber=").append(sequenceNumber);
            sb.append(", acknowledgementNumber=").append(acknowledgementNumber);
            sb.append(", headerLength=").append(headerLength);
            sb.append(", window=").append(window);
            sb.append(", checksum=").append(checksum);
            sb.append(", flags=");
            if (isFIN()) sb.append(" FIN");
            if (isSYN()) sb.append(" SYN");
            if (isRST()) sb.append(" RST");
            if (isPSH()) sb.append(" PSH");
            if (isACK()) sb.append(" ACK");
            if (isURG()) sb.append(" URG");
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * UDP 头部，包含源/目标端口、长度和校验和
     *
     * @author ndroi
     */
    public static class UDPHeader
    {
        public int sourcePort;
        public int destinationPort;

        public int length;
        public int checksum;


        /**
         * 默认构造函数
         */
        public UDPHeader()
        {

        }

        /**
         * 从字节缓冲区解析 UDP 头部
         *
         * @param buffer 包含 UDP 头部字节的缓冲区
         */
        private UDPHeader(ByteBuffer buffer)
        {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.length = BitUtils.getUnsignedShort(buffer.getShort());
            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
        }

        private void fillHeader(ByteBuffer buffer)
        {
            buffer.putShort((short) this.sourcePort);
            buffer.putShort((short) this.destinationPort);

            buffer.putShort((short) this.length);
            buffer.putShort((short) this.checksum);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder("UDPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", length=").append(length);
            sb.append(", checksum=").append(checksum);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 无符号数值转换工具
     *
     * @author ndroi
     */
    private static class BitUtils
    {
        /**
         * 将有符号字节转换为无符号短整型
         *
         * @param value 有符号字节值
         * @return 无符号短整型值
         */
        private static short getUnsignedByte(byte value)
        {
            return (short) (value & 0xFF);
        }

        /**
         * 将有符号短整型转换为无符号整型
         *
         * @param value 有符号短整型值
         * @return 无符号整型值
         */
        private static int getUnsignedShort(short value)
        {
            return value & 0xFFFF;
        }

        /**
         * 将有符号整型转换为无符号长整型
         *
         * @param value 有符号整型值
         * @return 无符号长整型值
         */
        private static long getUnsignedInt(int value)
        {
            return value & 0xFFFFFFFFL;
        }
    }
}

