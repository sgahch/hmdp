package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author Ynchen
 * @since 2025-05-28
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Value(value = "${spring.servlet.multipart.max-file-size}")
    private Long MAX_AVATAR_SIZE ; // 50MB


    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // 注意：登出时也应清理Redis和ThreadLocal
        // UserHolder.removeUser(); // 清理ThreadLocal
        // stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + UserHolder.getUser().getToken()); // 清理Redis
        return userService.logout(); // 假设userService.logout()已经处理了这些
    }

    @GetMapping("/me")
    public Result me(){
        //  获取当前登录的用户并返回
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id")Long userId){
        User user = userService.getById(userId);
        if (user==null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 更新用户基本信息（昵称、头像）
     */
    @PutMapping("/update")
    public Result updateUser(@RequestBody User user) {
        try {
            UserDTO currentUserDTO = UserHolder.getUser(); // 从ThreadLocal获取当前用户信息
            if (currentUserDTO == null) {
                return Result.fail("用户未登录");
            }

            Long userId = currentUserDTO.getId();

            // 1. 创建更新对象，只更新允许的字段
            User updateUser = new User();
            updateUser.setId(userId); // 确保更新的是当前用户

            // 判断哪些字段需要更新，并设置到updateUser对象中
            boolean hasUpdate = false;
            if (user.getNickName() != null) {
                updateUser.setNickName(user.getNickName());
                hasUpdate = true;
            }
            if (user.getIcon() != null) {
                updateUser.setIcon(user.getIcon());
                hasUpdate = true;
            }

            // 如果没有要更新的字段，直接返回成功（或者提示没有变化）
            if (!hasUpdate) {
                return Result.ok("没有需要更新的信息");
            }

            // 2. 更新数据库
            boolean dbUpdateSuccess = userService.updateById(updateUser);
            if (!dbUpdateSuccess) {
                return Result.fail("更新失败");
            }

            // 3. 从数据库重新查询最新的用户信息
            // 这一步非常重要，确保获取到所有字段的最新值，并用于更新Redis和ThreadLocal
            User latestUserEntity = userService.getById(userId);

            if (latestUserEntity == null) {
                // 数据库更新成功但查询失败，理论上不应该发生，但作为健壮性处理
                return Result.fail("获取最新用户信息失败，请重试");
            }

            update_redis_TheardLocal(latestUserEntity,currentUserDTO);

            return Result.ok("更新成功");
        } catch (Exception e) {
            log.error("更新用户基本信息失败", e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 头像上传接口
     * 上传文件并自动更新用户头像字段
     */
    @PostMapping("/avatar/upload")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            UserDTO currentUserDTO = UserHolder.getUser();
            if (currentUserDTO == null) {
                return Result.fail("用户未登录");
            }

            // 1. 验证文件
            if (file.isEmpty()) {
                return Result.fail("请选择要上传的文件");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isImageFile(originalFilename)) {
                return Result.fail("请上传图片文件");
            }

            // 检查文件大小 (5MB)
            if (file.getSize() > MAX_AVATAR_SIZE) {
                return Result.fail("图片大小不能超过50MB");
            }

            // 2. 确定上传目录
            String uploadDir = "C:/Users/Ynchen/Desktop/hmdp-orgin/nginx-1.18.0/html/hmdp/imgs";
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) {
                uploadDirFile.mkdirs();
            }

            // 3. 生成新文件名
            String fileExtension = StrUtil.subAfter(originalFilename, ".", true);
            String newFileName = "avatar_" + currentUserDTO.getId() + "_" + System.currentTimeMillis() + "." + fileExtension;

            // 4. 保存文件
            File targetFile = new File(uploadDir, newFileName);
            file.transferTo(targetFile);

            // 5. 生成访问URL
            String avatarUrl = "/imgs/" + newFileName;

            // 6. 更新数据库中的用户头像
            User updateUser = new User();
            updateUser.setId(currentUserDTO.getId());
            updateUser.setIcon(avatarUrl);

            boolean dbUpdateSuccess = userService.updateById(updateUser);
            if (!dbUpdateSuccess) {
                // 如果数据库更新失败，删除已上传的文件
                FileUtil.del(targetFile);
                return Result.fail("头像更新失败");
            }

            // 7. 从数据库重新查询最新的用户信息
            User latestUserEntity = userService.getById(currentUserDTO.getId());
            if (latestUserEntity == null) {
                return Result.fail("获取最新用户信息失败");
            }

            update_redis_TheardLocal(latestUserEntity,currentUserDTO);


            log.info("用户 {} 头像上传成功: {}", currentUserDTO.getId(), avatarUrl);
            return Result.ok(avatarUrl);

        } catch (IOException e) {
            log.error("头像上传失败", e);
            return Result.fail("头像上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("头像上传过程中发生错误", e);
            return Result.fail("头像上传失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否为图片文件
     */
    private boolean isImageFile(String filename) {
        String extension = StrUtil.subAfter(filename, ".", true).toLowerCase();
        return "jpg".equals(extension) || "jpeg".equals(extension) ||
               "png".equals(extension) || "gif".equals(extension) ||
               "bmp".equals(extension) || "webp".equals(extension);
    }

    /**
     * 更新用户详细信息（如城市、个人介绍、性别、生日、积分、会员级别、粉丝数量、关注的人的数量等）
     * 注意：此方法仅更新数据库中的UserInfo，如果UserDTO中也包含UserInfo相关字段且需同步，
     * 则也需更新Redis和ThreadLocal中的UserDTO。
     */
    @PutMapping("/info/update")
    public Result updateUserInfo(@RequestBody UserInfo userInfo) {
        try {
            UserDTO currentUserDTO = UserHolder.getUser();
            if (currentUserDTO == null) {
                return Result.fail("用户未登录");
            }
            Long userId = currentUserDTO.getId();

            // 查询是否已存在用户信息
            UserInfo existingInfo = userInfoService.getById(userId);
            boolean success;
            if (existingInfo != null) {
                // 更新现有信息
                userInfo.setUserId(userId); // 确保更新的是当前用户的详情
                success = userInfoService.updateById(userInfo);
            } else {
                // 创建新信息
                userInfo.setUserId(userId); // 为新创建的用户详情设置ID
                success = userInfoService.save(userInfo);
            }
            if (!success) {
                return Result.fail("更新失败");
            }

            // 重要：
            // 如果您的 `UserDTO` 类 (存在于 Redis 和 UserHolder 中) 包含了 `UserInfo` 的字段
            // （例如 `introduce`, `gender`, `city`, `birthday` 等），
            // 那么您需要在这里重新获取最新的 `UserInfo` 数据，然后将这些字段更新到 `currentUserDTO`，
            // 接着像 `updateUser` 方法那样，把更新后的 `currentUserDTO` 存回 Redis 并更新 `UserHolder`。
            // 否则，如果 `UserDTO` 只包含 `User` 表的基本信息，而 `UserInfo` 是完全独立通过 `/user/info/{id}` 接口查询的，
            // 那么这里无需更新 Redis 和 ThreadLocal 中的 `UserDTO`。
            //
            // 鉴于您前端的 `info` 对象是独立绑定的，并且 `loadUserInfo` 依赖 `/user/info/{id}` 获取，
            // 默认情况下这里不需要更新UserDTO的Redis缓存。
            // 但是，您在原来代码中添加了 `stringRedisTemplate.delete(RedisConstants.USER_INFO_KEY + currentUser.getId());`
            // 这暗示您可能在其他地方对 `UserInfo` 也进行了 Redis 缓存。
            // 如果 `RedisConstants.USER_INFO_KEY` 是用于缓存 `UserInfo` 对象的，
            // 那么在这里删除旧缓存，以确保下次查询时从数据库获取最新，这是正确的做法。
            //
            // 如果您没有对 `UserInfo` 进行单独缓存，或者 `USER_INFO_KEY` 不存在，那么删除操作无意义。
            // 如果您确实对 `UserInfo` 进行了独立缓存，那么删除旧缓存是必要的，
            // 这样前端 `loadUserInfo` 调用 `/user/info/{id}` 时就能得到最新数据。
            // **我保留了您之前的删除逻辑，假设它有其对应之处。**

            return Result.ok("更新成功");
        } catch (Exception e) {
            log.error("更新用户详细信息失败", e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    public void update_redis_TheardLocal(User latestUserEntity, UserDTO currentUserDTO){
        // 8. 更新Redis缓存和ThreadLocal
        UserDTO latestUserDTO = BeanUtil.copyProperties(latestUserEntity, UserDTO.class);
        latestUserDTO.setToken(currentUserDTO.getToken());

        // 更新Redis缓存
        String tokenKey = RedisConstants.LOGIN_USER_KEY + latestUserDTO.getToken();
        Map<String, Object> userMap = BeanUtil.beanToMap(latestUserDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 更新ThreadLocal
        UserHolder.saveUser(latestUserDTO);
    }
}