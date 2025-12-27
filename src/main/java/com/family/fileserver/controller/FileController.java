package com.family.fileserver.controller;

import com.family.fileserver.util.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
@Controller
public class FileController {
    @Value("${file.shared.path}")
    private String defaultDir;

    // 根路径访问，统一重定向到 /file/list，保持路径一致性
    @GetMapping(value = {"", "/"})
    public RedirectView index() {
        return new RedirectView("/file/list");
    }

    // 文件列表查询接口，统一带 /file 前缀，与上传重定向路径匹配
    @GetMapping("/file/list")
    public String listFile(@RequestParam(value = "dir", required = false) String dir, Model model) {
        // 处理 dir 参数，解码 + 非空判断
        String currentDir;
        try {
            if (dir == null || dir.trim().isEmpty()) {
                currentDir = defaultDir;
            } else {
                currentDir = URLDecoder.decode(dir, StandardCharsets.UTF_8.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            currentDir = defaultDir; // 异常时使用默认目录兜底
        }

        // 验证目录是否存在
        File dirFile = new File(currentDir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            currentDir = defaultDir; // 目录不存在时，切换到默认目录
            dirFile = new File(currentDir);
        }

        // 打印目录信息，便于排查
        System.out.println("=== 后端目录排查 ===");
        System.out.println("当前目录路径：" + currentDir);
        System.out.println("目录是否存在：" + dirFile.exists());
        System.out.println("是否是目录：" + dirFile.isDirectory());

        // 获取文件列表和面包屑（仅调用一次，避免冗余）
        List<FileUtils.FileInfo> fileList = FileUtils.listFiles(currentDir);
        List<FileUtils.Breadcrumb> breadcrumbs = FileUtils.buildBreadcrumbs(currentDir);
        List<FileUtils.FileInfo> imageList = fileList.stream().filter(FileUtils.FileInfo::isImage).filter(f -> !f.isDir()).toList();
        System.out.println("查询到的文件/文件夹数量：" + fileList.size());

        // 传递数据到前端，键名与前端严格一致（大写L fileList）
        model.addAttribute("currentDirPath", currentDir);
        model.addAttribute("fileList", fileList); // 核心数据，前端遍历依赖
        model.addAttribute("breadcrumbs", breadcrumbs);
        model.addAttribute("imageList", imageList);

        return "index"; // 对应 templates 目录下的 index.html
    }

    // 文件上传接口，上传后重定向回 /file/list
    @PostMapping("/file/upload") // 统一带 /file 前缀，更规范
    public ResponseEntity<Object> uploadFile(@RequestParam("file") MultipartFile[] files,@RequestParam("targetDir") String targetDir) {
        List<String> resultList = new ArrayList<>(); // 存储每个文件的上传结果
        boolean hasError = false;
        // 对目标目录编码，避免中文/特殊字符导致跳转异常
//        String encodedTargetDir = "";
//        if (targetDir != null && !targetDir.trim().isEmpty()) {
//            try {
//                encodedTargetDir = URLEncoder.encode(targetDir, StandardCharsets.UTF_8);
//            } catch (Exception e) {
//                e.printStackTrace();
//                encodedTargetDir = targetDir;
//            }
//        }
        // 遍历上传文件
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                resultList.add("跳过空文件");
                continue; // 跳过空文件
            }
            try {
                File uploadDir = new File(targetDir); // 实际写入还是用原始路径
                if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                    // 提示中用编码后的路径，避免中文乱码
                    resultList.add("文件[" + file.getOriginalFilename() + "]上传失败：目录[" + targetDir + "]创建失败");
                    hasError = true;
                    continue;
                }

                FileUtils.uploadFile(file, targetDir);

                File destFile = new File(targetDir, file.getOriginalFilename());
                if (!destFile.exists() || destFile.length() == 0) {
                    resultList.add("文件[" + file.getOriginalFilename() + "]上传失败：目录[" + targetDir + "]写入空文件");
                    hasError = true;
                } else {
                    resultList.add("文件[" + file.getOriginalFilename() + "]上传成功（存储路径：" + targetDir + "）");
                }

            } catch (IOException e) {
                e.printStackTrace();
                resultList.add("文件[" + file.getOriginalFilename() + "]上传失败：目录[" + targetDir + "]，原因：" + e.getMessage());
                hasError = true;
            }
        }
        if (hasError) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("部分文件上传失败：" + resultList);
        } else {
            return ResponseEntity.ok("所有文件上传成功：" + resultList);
        }
    }

    /**
     * 动态访问文件/图片接口（支持子目录）
     * @param dir 当前目录（子目录路径，如 D:/TestFileDir/子目录1）
     * @param fileName 文件名（如 xxx.jpg）
     * @return 图片/文件响应
     */
    @GetMapping("/file/access")
    public ResponseEntity<Resource> accessFile(
            @RequestParam(value = "dir", required = false) String dir,
            @RequestParam("fileName") String fileName) {
        try {
            // 1. 处理当前目录（默认使用根目录，否则解码子目录路径）
            String currentDir;
            if (dir == null || dir.trim().isEmpty()) {
                currentDir = defaultDir;
            } else {
                currentDir = URLDecoder.decode(dir, StandardCharsets.UTF_8);
            }

            // 2. 拼接完整本地文件路径
            File targetFile = new File(currentDir, fileName);
            // 校验文件是否存在
            if (!targetFile.exists() || targetFile.isDirectory()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 文件不存在返回404
            }

            // 3. 封装文件资源
            Resource resource = new FileSystemResource(targetFile);

            // 4. 设置响应头（支持浏览器预览图片/下载文件）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG); // 默认图片类型
            // 兼容 png/gif 等格式
            String fileNameLower = fileName.toLowerCase();
            if (fileNameLower.endsWith(".png")) {
                headers.setContentType(MediaType.IMAGE_PNG);
            } else if (fileNameLower.endsWith(".gif")) {
                headers.setContentType(MediaType.IMAGE_GIF);
            }

            // 5. 返回文件响应
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}