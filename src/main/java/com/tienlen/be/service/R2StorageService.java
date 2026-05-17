package com.tienlen.be.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    @Override
    public String uploadFile(MultipartFile file, String path) throws IOException {
        String fileName = file.getOriginalFilename();
        String key = (path.endsWith("/") ? path : path + "/") + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return String.format("%s/%s", publicUrl, key);
    }

    @Override
    public String uploadBytes(byte[] content, String fileName, String contentType, String path) {
        String key = (path.endsWith("/") ? path : path + "/") + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

        return String.format("%s/%s", publicUrl, key);
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            String key = fileUrl.replace(publicUrl + "/", "");
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error("Failed to delete file from R2: {}", fileUrl, e);
        }
    }
}
