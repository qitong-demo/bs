package com.friendlystation.platform.file;

import com.friendlystation.platform.auth.RequireRole;
import com.friendlystation.platform.common.ApiResponse;
import com.friendlystation.platform.common.BusinessException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 本地文件上传：演示环境用于资质、站点照片。 */
@RestController
@RequestMapping("/api/file")
public class FileController {
    private static final long MAX_SIZE = 5 * 1024 * 1024L;
    private static final Path ROOT = Paths.get("uploads").toAbsolutePath().normalize();

    @PostMapping("/upload")
    @RequireRole({"USER", "STATION_ADMIN", "PLATFORM_ADMIN"})
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!List.of("jpg", "jpeg", "png", "webp", "pdf").contains(ext)) {
            throw new BusinessException("只支持 jpg/png/webp/pdf 文件");
        }
        try {
            Files.createDirectories(ROOT);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            file.transferTo(ROOT.resolve(name).toFile());
            return ApiResponse.ok(Map.of("url", "/api/file/" + name, "name", original, "size", file.getSize()));
        } catch (IOException exception) {
            throw new BusinessException("文件保存失败，请稍后重试");
        }
    }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path path = ROOT.resolve(name).normalize();
        if (!path.startsWith(ROOT) || !Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = name.endsWith(".pdf") ? "application/pdf" : "image/" + name.substring(name.lastIndexOf('.') + 1);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }
}
