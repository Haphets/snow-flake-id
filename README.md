唯一ID
雪花算法的java实现，其他还有GUID实现（https://www.guidgenerator.com/online-guid-generator.aspx）
#说明
SnowFlake算法生成id的结果是一个64bit大小的整数
## 1位 
二进制中最高位为1的都是负数，但是我们id一般都用的是正整数，所以这个最高位固定位0

## 41位时间戳
用来记录时间戳

## 10位工作机器ID
分为5位数据中心ID(datacenterId)和5位工作机器ID(workerId)

## 12位序列号
用来记录同毫秒内产生的不同id，理论上毫秒内可产生2^12=4096个ID序号

#SnowFlake优势：
在Java中64bit的整数是long类型，所以在Java中SnowFlake算法生成的id就是long来存储的
1. 所有生成的id按时间趋势递增
2. 整个分布式系统内不会产生重复id（因为有datacenterId和workerId来做区分)




