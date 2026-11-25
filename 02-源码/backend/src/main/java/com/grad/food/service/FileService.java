package com.grad.food.service;

import com.grad.food.common.BizException;
import com.grad.food.common.UserContext;
import com.grad.food.entity.SysFile;
import com.grad.food.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传：统一保存到项目根目录 uploads/<bizType>/yyyyMM/ 下，返回相对路径。
 * 只允许图片与 PDF，拒绝外链式提交；前端只用上传组件，不允许手填 URL。
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private final SysFileMapper fileMapper;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    private static final List<String> ALLOW = List.of("jpg", "jpeg", "png", "webp", "pdf");

    public SysFile upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOW.contains(ext)) {
            throw new BizException("仅支持 jpg / png / webp / pdf 格式");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BizException("单个文件不得超过 20MB");
        }
        String type = (bizType == null || bizType.isBlank()) ? "common" : bizType.replaceAll("[^a-zA-Z]", "");
        String month = LocalDate.now().toString().substring(0, 7).replace("-", "");
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;
        try {
            Path dir = Paths.get(uploadDir, type, month).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            file.transferTo(target.toFile());

            SysFile f = new SysFile();
            f.setBizType(type);
            f.setFileName(name);
            f.setOriginalName(original);
            f.setPath("/uploads/" + type + "/" + month + "/" + name);
            f.setFileSize(file.getSize());
            f.setContentType(file.getContentType());
            f.setUploaderId(UserContext.userId());
            f.setUploaderName(UserContext.realName());
            f.setCreateTime(LocalDateTime.now());
            fileMapper.insert(f);
            return f;
        } catch (IOException e) {
            throw new BizException("文件保存失败：" + e.getMessage());
        }
    }
}
