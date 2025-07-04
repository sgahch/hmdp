package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.config.UploadConfig;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @Resource
    private UploadConfig uploadConfig;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            log.debug("开始上传文件: {}", originalFilename);

            // 检查上传目录是否存在
            File uploadDir = new File(uploadConfig.getImageDir());
            if (!uploadDir.exists()) {
                log.info("创建上传目录: {}", uploadConfig.getImageDir());
                uploadDir.mkdirs();
            }

            // 生成新文件名
            String fileName = createNewFileName(originalFilename);

            // 完整的文件路径
            File targetFile = new File(uploadConfig.getImageDir(), fileName);
            log.debug("目标文件路径: {}", targetFile.getAbsolutePath());

            // 保存文件
            image.transferTo(targetFile);

            // 返回结果
            log.debug("文件上传成功，相对路径: {}, 绝对路径: {}", fileName, targetFile.getAbsolutePath());
            return Result.ok(fileName);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(uploadConfig.getImageDir(), filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        log.debug("删除文件: {}", file.getAbsolutePath());
        return Result.ok();
    }

    /**
     * 调试接口：检查上传配置
     */
    @GetMapping("/debug/config")
    public Result debugConfig() {
        File uploadDir = new File(uploadConfig.getImageDir());

        Map<String, Object> config = new HashMap<>();
        config.put("imageDir", uploadConfig.getImageDir());
        config.put("nginxPrefix", uploadConfig.getNginxPrefix());
        config.put("dirExists", uploadDir.exists());
        config.put("dirAbsolutePath", uploadDir.getAbsolutePath());

        if (uploadDir.exists()) {
            config.put("dirCanWrite", uploadDir.canWrite());
            config.put("dirCanRead", uploadDir.canRead());

            // 列出blogs目录下的子目录
            File blogsDir = new File(uploadDir, "blogs");
            if (blogsDir.exists()) {
                String[] subDirs = blogsDir.list();
                config.put("blogsSubDirs", subDirs != null ? subDirs.length : 0);
            }
        }

        log.info("上传配置调试信息: {}", config);
        return Result.ok(config);
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(uploadConfig.getImageDir(), StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
