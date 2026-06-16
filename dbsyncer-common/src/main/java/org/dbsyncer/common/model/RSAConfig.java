/**
 * DBSyncer Copyright 2020-2026 All Rights Reserved.
 */
package org.dbsyncer.common.model;

/**
 * RSA配置
 *
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public class RSAConfig {

    /**
     * 密钥长度
     */
    private int keyLength = 2048;

    /**
     * RSA公钥
     */
    private String publicKey;

    /**
     * RSA私钥
     */
    private String privateKey;

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
}