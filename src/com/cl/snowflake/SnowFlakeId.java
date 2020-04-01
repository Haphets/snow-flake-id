package com.cl.snowflake;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: li.chen
 * Date: 2020-03-31 18:11
 */
public class SnowFlakeId {

    private long workerId;
    private long datacenterId;

    /**
     * 开始时间戳
     */
    private final static long TWEPOCH = 1585670400000L;
    /**
     * 机器ID位数
     */
    private final static long WORKER_BITS = 5L;
    /**
     * 数据中心位数
     */
    private final static long DATA_CENTER_BITS = 5L;

    /**
     * 序列号位数
     */
    private final static long SEQUENCE_BITS = 12L;

    /**
     * 最大机器ID 2^5 - 1 = 31
     */
    private final static long MAX_WORKER_ID = -1L ^ (-1L << WORKER_BITS);
    /**
     * 最大数据中心 2^5 - 1 = 31
     */
    private final static long MAX_DATA_CENTER_ID = -1L ^ (-1L << DATA_CENTER_BITS);
    /**
     * 序列号能存储的最大正整数 2^12 -1 = 4095
     */
    private final static long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);

    /**
     * 12位
     */
    private final static long WORKER_SHIFT = SEQUENCE_BITS;
    /**
     * 17位
     */
    private final static long DATA_CENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    /**
     * 22位
     */
    private final static long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATA_CENTER_BITS;

    /**
     * 序列号
     */
    private long sequence = 0L;
    /**
     * 上一次时间戳
     */
    private long lastTimestamp = -1L;

    public SnowFlakeId(){
        this.datacenterId = getDatacenterId(MAX_DATA_CENTER_ID);
        this.workerId = getMaxWorkerId(datacenterId, MAX_WORKER_ID);
    }

    public SnowFlakeId(long datacenterId, long workerId) {
        // sanity check for workerId
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("机器ID不能大于 %d 或小于 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATA_CENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("数据中心ID不能大于 %d 或小于 0", MAX_DATA_CENTER_ID));
        }

        this.workerId = workerId;
        this.datacenterId = datacenterId;

        System.out.println(String.format("timestamp左移 %d, datacenterID位数 %d, workerID位数 %d, 序列号位数 %d, workerID %d, datacenterId %d",
                TIMESTAMP_SHIFT, DATA_CENTER_BITS, WORKER_BITS, SEQUENCE_BITS, workerId,datacenterId));
    }

    /**
     * 产生ID
     * @return
     */
    public long nextId() {

        ReadWriteLock readLock = new ReentrantReadWriteLock();
        try {
            readLock.readLock().lock();
            long now = now();

            if (now < lastTimestamp) {
                System.err.printf("当前机器时间出现倒退,本次请求时间:" + now + ",上次请求时间:" + lastTimestamp + ",请尽快检查");
                throw new RuntimeException("当前机器时间出现倒退,本次请求时间:" + now + ",上次请求时间:" + lastTimestamp + ",请尽快检查");
            }
            // 相同毫秒内，序列号自增
            if (lastTimestamp == now) {
                // 位与运算(都为1，结果才为1，否则结果为0) 保证计算的结果范围始终是 0-4095
                // 支持毫秒内4095个id的产生
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    now = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            //当前时间赋值最后一次请求时间
            lastTimestamp = now;
            // ((now - twepoch) << timestampLeftShift) 左移22位,移到 时间戳段的22位
            // (datacenterId << datacenterIdShift)  左移17位，移到数据中心段的5位
            // (workerId << workerIdShift) 左移12位，移到机器段的5位
            // sequence 序列号12位
            // 最后做位或运算(只要有1结果就是1),保证了64位 都有各自的数据段意义
            return (now - TWEPOCH) << TIMESTAMP_SHIFT |
                    datacenterId << DATA_CENTER_SHIFT |
                    workerId << WORKER_SHIFT |
                    sequence;
        } finally {
            readLock.readLock().unlock();
        }
    }

    /**
     * 产生下一个时间戳
     * @param lastTimestamp
     * @return
     */
    private long tilNextMillis(long lastTimestamp) {
        long now = now();
        while (now <= lastTimestamp) {
            now = now();
        }
        return now;
    }

    /**
     * <p>
     * 获取 maxWorkerId
     * </p>
     */
    protected static long getMaxWorkerId(long datacenterId, long maxWorkerId) {
        StringBuffer mpid = new StringBuffer();
        mpid.append(datacenterId);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (!name.isEmpty()) {
            /*
             * GET jvmPid
             */
            mpid.append(name.split("@")[0]);
        }
        /*
         * MAC + PID 的 hashcode 获取16个低位
         */
        return (mpid.toString().hashCode() & 0xffff) % (maxWorkerId + 1);
    }

    /**
     * <p>
     * 数据标识id部分
     * </p>
     */
    protected static long getDatacenterId(long maxDatacenterId) {
        long id = 0L;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            if (network == null) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                id = ((0x000000FF & (long) mac[mac.length - 1])
                        | (0x0000FF00 & (((long) mac[mac.length - 2]) << 8))) >> 6;
                id = id % (maxDatacenterId + 1);
            }
        } catch (Exception e) {
            System.out.println("获取DatacenterId发生错误: " + e.getMessage());
        }
        return id;
    }

    /**
     * 获取当前时间
     *
     * @return
     */
    private long now() {
        return System.currentTimeMillis();
    }
}
