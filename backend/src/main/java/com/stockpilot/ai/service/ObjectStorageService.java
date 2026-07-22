package com.stockpilot.ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorageService {
    String store(String key, MultipartFile file) throws IOException;
    InputStream read(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
}
