package org.dbsyncer.biz.vo;

/**
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public class VersionVo {

    /**
     * 应用名称
     */
    private String appName;
    /**
     * 版权详细
     */
    private String appCopyRight;
    /**
     * 水印
     */
    private String watermark;

    public VersionVo(String appName, String appCopyRight) {
        this.appName = appName;
        this.appCopyRight = appCopyRight;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppCopyRight() {
        return appCopyRight;
    }

    public String getWatermark() {
        return watermark;
    }

    public void setWatermark(String watermark) {
        this.watermark = watermark;
    }
}
