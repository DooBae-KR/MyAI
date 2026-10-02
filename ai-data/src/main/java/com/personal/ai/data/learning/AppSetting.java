package com.personal.ai.data.learning;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 앱 설정 한 줄(key → value). 비밀 값은 저장하지 않는다. */
@Entity
@Table(name = "APP_SETTING")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String value;

    private LocalDateTime updatedAt = LocalDateTime.now();

    protected AppSetting() {}

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) {
        this.value = value;
        this.updatedAt = LocalDateTime.now();
    }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
