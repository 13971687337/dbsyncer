/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.consumer;

import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.flush.impl.BufferActuatorRouter;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.plugin.PluginFactory;
import org.dbsyncer.plugin.enums.ProcessEnum;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.listener.QuartzListenerContext;
import org.dbsyncer.sdk.listener.Watcher;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public final class ParserConsumer implements Watcher {
    private final BufferActuatorRouter bufferActuatorRouter;
    private final ProfileComponent profileComponent;
    private final PluginFactory pluginFactory;
    private final LogService logService;
    private final BiConsumer<String, String> eventRecorder;
    private final String metaId;

    public ParserConsumer(BufferActuatorRouter bufferActuatorRouter, ProfileComponent profileComponent, PluginFactory pluginFactory, LogService logService, BiConsumer<String, String> eventRecorder, String metaId, List<TableGroup> tableGroups) {
        this.bufferActuatorRouter = bufferActuatorRouter;
        this.profileComponent = profileComponent;
        this.pluginFactory = pluginFactory;
        this.logService = logService;
        this.eventRecorder = eventRecorder;
        this.metaId = metaId;
        // 注册到路由服务中
        bufferActuatorRouter.bind(metaId, tableGroups);
    }

    @Override
    public void changeEventBefore(QuartzListenerContext context) {
        pluginFactory.process(context, ProcessEnum.BEFORE);
    }

    @Override
    public void changeEvent(ChangedEvent event) {
        if (eventRecorder != null) {
            eventRecorder.accept(metaId, event.getEvent());
        }
        bufferActuatorRouter.execute(metaId, event);
    }

    @Override
    public void flushEvent(Map<String, String> snapshot) {
        Meta meta = profileComponent.getMeta(metaId);
        if (meta != null) {
            meta.setSnapshot(snapshot);
            profileComponent.editConfigModel(meta);
        }
    }

    @Override
    public void errorEvent(Exception e) {
        logService.log(LogType.TableGroupLog.INCREMENT_FAILED, e.getMessage());
    }

    @Override
    public long getMetaUpdateTime() {
        Meta meta = profileComponent.getMeta(metaId);
        return meta != null ? meta.getUpdateTime() : 0L;
    }
}