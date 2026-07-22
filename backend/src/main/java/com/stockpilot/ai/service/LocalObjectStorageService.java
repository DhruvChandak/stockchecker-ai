package com.stockpilot.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalObjectStorageService implements ObjectStorageService {
    private final Path root;

    public LocalObjectStorageService(@Value("${app.storage.local-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(String key, MultipartFile file) throws IOException {
        var destination = root.resolve(key).normalize();
        if (!destination.startsWith(root)) {
            throw new IOException("Invalid storage key");
        }
        Files.createDirectories(destination.getParent());
        file.transferTo(destination);
        return key;
    }

    @Override
    public InputStream read(String storageKey) throws IOException {
        var source = root.resolve(storageKey).normalize();
        if (!source.startsWith(root)) {
            throw new IOException("Invalid storage key");
        }
        return Files.newInputStream(source);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        var source = root.resolve(storageKey).normalize();
        if (!source.startsWith(root)) {
            throw new IOException("Invalid storage key");
        }
        Files.deleteIfExists(source);
    }
}
