package org.dbsyncer.common.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 滑动窗口计数器，O(1) 开销，不创建对象
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2026/06/16
 */
public class SlidingWindow {
    private final AtomicLongArray buckets;
    private final int size;
    private volatile int cursor;
    private final AtomicLong total = new AtomicLong();

    public SlidingWindow(int size) {
        this.size = size;
        this.buckets = new AtomicLongArray(size);
    }

    public void add(long n) {
        int idx = cursor % size;
        long old = buckets.getAndSet(idx, n);
        total.addAndGet(n - old);
        cursor++;
    }

    public long sum() {
        return total.get();
    }

    public double avg() {
        long s = sum();
        return s == 0 ? 0 : (double) s / size;
    }
}
