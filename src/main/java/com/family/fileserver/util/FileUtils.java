package com.family.fileserver.util;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 文件操作工具类
 * 包含：文件上传、目录遍历、文件信息封装、面包屑构建、重名文件处理等功能
 */
@Component()
public class FileUtils {

    // 允许上传的文件后缀名（可按需扩展）
    private static final List<String> ALLOWED_SUFFIXES = new ArrayList<String>() {{
        add(".jpg");
        add(".jpeg");
        add(".png");
        add(".gif");
        add(".pdf");
        add(".doc");
        add(".docx");
        add(".txt");
    }};

    /**
     * 上传文件到指定目录（当前目录），自动处理重名文件，不生成固定上传目录
     * @param file 待上传的文件
     * @param targetDir 目标目录（当前点击的目录，前端传递）
     * @throws IOException 上传异常
     */
    public static void uploadFile(MultipartFile file, String targetDir) throws IOException {
        // 1. 校验文件是否为空
        if (file.isEmpty()) {
            throw new IOException("待上传文件为空");
        }

        // 2. 校验文件类型是否允许
        String fileName = file.getOriginalFilename();
        if (!isAllowedFile(fileName)) {
            throw new IOException("文件类型不允许上传，仅支持：" + ALLOWED_SUFFIXES.toString());
        }

        // 3. 构建目标目录路径，动态创建目录（不存在则递归创建）
        Path targetDirPath = Paths.get(targetDir);
        if (!Files.exists(targetDirPath)) {
            Files.createDirectories(targetDirPath);
        }

        // 4. 处理重名文件，避免覆盖
        String finalFileName = handleDuplicateFileName(targetDir, fileName);

        // 5. 构建目标文件路径
        Path targetFilePath = targetDirPath.resolve(finalFileName);

        // 6. 写入文件到目标目录
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFilePath);
        }
    }

    /**
     * 处理重名文件，自动添加序号后缀（如：test(1).jpg）
     * @param dir 目录路径
     * @param fileName 原始文件名
     * @return 处理后的唯一文件名
     */
    public static String handleDuplicateFileName(String dir, String fileName) {
        File targetDir = new File(dir);
        File targetFile = new File(targetDir, fileName);

        // 如果文件不存在，直接返回原始文件名
        if (!targetFile.exists()) {
            return fileName;
        }

        // 拆分文件名和后缀名
        String baseName = "";
        String suffix = "";
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex != -1) {
            baseName = fileName.substring(0, lastDotIndex);
            suffix = fileName.substring(lastDotIndex);
        } else {
            baseName = fileName;
        }

        // 循环判断文件是否存在，添加序号后缀
        int counter = 1;
        while (targetFile.exists()) {
            String newFileName = baseName + "(" + counter + ")" + suffix;
            targetFile = new File(targetDir, newFileName);
            counter++;
        }

        return targetFile.getName();
    }

    /**
     * 校验文件是否允许上传
     * @param fileName 文件名
     * @return true-允许，false-不允许
     */
    public static boolean isAllowedFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        // 忽略大小写判断
        String lowerFileName = fileName.toLowerCase();
        for (String suffix : ALLOWED_SUFFIXES) {
            if (lowerFileName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 遍历指定目录下的所有文件和文件夹，封装为FileInfo列表
     * @param dirPath 目录路径
     * @return 目录下的文件/文件夹信息列表
     */
    public static List<FileInfo> listFiles(String dirPath) {
        List<FileInfo> fileInfoList = new ArrayList<>();
        File dir = new File(dirPath);

        // 校验目录是否存在且是目录
        if (!dir.exists() || !dir.isDirectory()) {
            return fileInfoList;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return fileInfoList;
        }

        // 遍历文件/文件夹，封装信息
        for (File file : files) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setName(file.getName());
            fileInfo.setPath(file.getAbsolutePath());
            fileInfo.setDir(file.isDirectory());
            fileInfo.setSize(file.length());
            // 格式化文件大小（如：1.5MB）
            fileInfo.setFormattedSize(formatFileSize(file.length()));
            // 编码文件路径（用于前端跳转）
            fileInfo.setEncodedPath(encodeFilePath(file.getAbsolutePath()));
            fileInfo.setImage(isImageFile(file));
            try {
                fileInfo.setEncodedPath(URLEncoder.encode(file.getAbsolutePath(), StandardCharsets.UTF_8.toString()));
            } catch (Exception e) {
                fileInfo.setEncodedPath(file.getAbsolutePath());
            }
            fileInfoList.add(fileInfo);
        }

        return fileInfoList;
    }

    /**
     * 构建面包屑导航数据
     * @param currentDir 当前目录路径
     * @return 面包屑列表
     */
    public static List<Breadcrumb> buildBreadcrumbs(String currentDir) {
        List<Breadcrumb> breadcrumbList = new ArrayList<>();
        File currentFile = new File(currentDir);

        // 递归向上构建面包屑，直到根目录
        buildBreadcrumbRecursive(currentFile, breadcrumbList);

        // 反转列表，得到从根目录到当前目录的顺序
        java.util.Collections.reverse(breadcrumbList);

        return breadcrumbList;
    }

    /**
     * 递归构建面包屑
     * @param file 当前文件/目录
     * @param breadcrumbList 面包屑列表
     */
    private static void buildBreadcrumbRecursive(File file, List<Breadcrumb> breadcrumbList) {
        if (file == null) {
            return;
        }

        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setName(file.getName());
        breadcrumb.setEncodedPath(encodeFilePath(file.getAbsolutePath()));
        breadcrumbList.add(breadcrumb);

        // 递归处理父目录
        buildBreadcrumbRecursive(file.getParentFile(), breadcrumbList);
    }

    /**
     * 格式化文件大小（B -> KB -> MB -> GB）
     * @param size 文件字节数
     * @return 格式化后的文件大小字符串
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", (double) size / 1024);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", (double) size / (1024 * 1024));
        } else {
            return String.format("%.2f GB", (double) size / (1024 * 1024 * 1024));
        }
    }

    /**
     * 编码文件路径，避免中文/特殊字符导致的URL解析异常
     * @param filePath 文件路径
     * @return 编码后的路径
     */
    public static String encodeFilePath(String filePath) {
        try {
            return URLEncoder.encode(filePath, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return filePath;
        }
    }

    public String getLocalIp() {
        try {
            // 遍历所有网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                // 过滤虚拟网卡、禁用网卡
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                // 遍历该网卡下的所有IP地址
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress ia = inetAddresses.nextElement();
                    // 过滤IPv6地址，只返回IPv4地址
                    if (ia instanceof Inet6Address) {
                        continue;
                    }
                    // 返回第一个有效局域网IP
                    return ia.getHostAddress();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        // 获取失败时，返回本地回环地址
        return "127.0.0.1";
    }

    /**
     * 文件信息封装类（内部类，用于存储文件/文件夹的关键信息）
     */
    public static class FileInfo {
        // 文件名
        private String name;
        // 文件绝对路径
        private String path;
        // 是否是目录
        private boolean isDir;
        // 文件大小（字节）
        private long size;
        // 格式化后的文件大小（如：1.5MB）
        private String formattedSize;
        // 编码后的文件路径（用于前端跳转）
        private String encodedPath;
        private String image;
        private boolean isImage;

        // 无参构造
        public FileInfo() {}

        // getter 和 setter 方法
        public String getImage() {
            return image;
        }

        public void setImage(boolean image) { isImage = image; }
        public boolean isImage() { return isImage; }
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isDir() {
            return isDir;
        }

        public void setDir(boolean dir) {
            isDir = dir;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getFormattedSize() {
            return formattedSize;
        }

        public void setFormattedSize(String formattedSize) {
            this.formattedSize = formattedSize;
        }

        public String getEncodedPath() {
            return encodedPath;
        }

        public void setEncodedPath(String encodedPath) {
            this.encodedPath = encodedPath;
        }
    }

    private static boolean isImageFile(File file) {
        if (file.isDirectory()) {
            return false; // 文件夹不是图片
        }
        String fileName = file.getName().toLowerCase(); // 转小写，避免大小写问题
        return (fileName.endsWith(".jpg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".webp"));
    }

    /**
     * 面包屑封装类（内部类，用于页面导航）
     */
    public static class Breadcrumb {
        // 面包屑名称（目录名）
        private String name;
        // 编码后的目录路径（用于跳转）
        private String encodedPath;

        // 无参构造
        public Breadcrumb() {}

        // getter 和 setter 方法
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEncodedPath() {
            return encodedPath;
        }

        public void setEncodedPath(String encodedPath) {
            this.encodedPath = encodedPath;
        }
    }
}