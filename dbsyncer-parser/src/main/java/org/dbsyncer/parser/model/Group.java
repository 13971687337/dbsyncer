/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public final class Group {
    private final List<String> index = new ArrayList<>();

    public synchronized void remove(String e) {
        index.remove(e);
    }

    public int size() {
        return index.size();
    }

    public List<String> getIndex() {
        return index;
    }

    public boolean contains(String id) {
        return index.contains(id);
    }

    public void add(String id) {
        index.add(id);
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }
}