package com.family.fileserver.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Value("${file.shared.path}")
    private String fileSharedPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置文件访问前缀，适配动态目录
        registry.addResourceHandler("/file/**")
                .addResourceLocations("file:" + fileSharedPath + "/") // 补充斜杠，避免路径拼接异常
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 手动解码路径，解决中文/特殊字符文件访问问题
                        String decodedPath;
                        try {
                            decodedPath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8.name());
                        } catch (Exception e) {
                            decodedPath = resourcePath; // 解码失败使用原路径

                        }

                        return super.getResource(decodedPath, location);
                    }
                });

        // 放行静态资源（若前端有 css/js 等，需配置）
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}