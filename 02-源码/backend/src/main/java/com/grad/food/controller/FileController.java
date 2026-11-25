package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.entity.SysFile;
import com.grad.food.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 文件上传接口：前端上传组件唯一入口，返回本地相对路径。 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public R<SysFile> upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) String bizType) {
        return R.ok(fileService.upload(file, bizType));
    }
}
