package org.dbsyncer.parser.event;

import org.dbsyncer.sdk.model.ChangedOffset;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ApplicationContextEvent;

import java.util.List;

/**
 * 刷新偏移量事件
 *
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public final class RefreshOffsetEvent extends ApplicationContextEvent {

    private final ChangedOffset changedOffset;

    /**
     * Create a new ContextStartedEvent.
     *
     * @param source the {@code ApplicationContext} that the event is raised for
     *               (must not be {@code null})
     */
    public RefreshOffsetEvent(ApplicationContext source, ChangedOffset changedOffset) {
        super(source);
        this.changedOffset = changedOffset;
    }

    public ChangedOffset getChangedOffset() {
        return changedOffset;
    }
}