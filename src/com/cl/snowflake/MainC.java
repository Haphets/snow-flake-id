package com.cl.snowflake;

/**
 * User: li.chen
 * Date: 2020-03-31 18:40
 */
public class MainC {
    /**
     * 产生唯一ID
     * @param args
     */
    public static void main(String[] args) {
        SnowFlakeId worker = new SnowFlakeId(1, 1);
        for (int i = 0; i < 2; i++) {
            System.out.println(worker.nextId());
        }
    }
}
