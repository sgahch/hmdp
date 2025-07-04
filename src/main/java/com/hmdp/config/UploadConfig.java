package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "hmdp.upload")
public class UploadConfig {
    
    /**
     * 图片上传目录
     */
    private String imageDir = "C:/Users/Ynchen/Desktop/hmdp/images";
    
    /**
     * nginx访问路径前缀
     */
    private String nginxPrefix = "/hmdp/images";
}
