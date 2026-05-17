package com.tienlen.be.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Uploads a file to R2 and returns the public URL.
     * 
     * @param file The file to upload
     * @param path The path/prefix within the bucket
     * @return The public URL of the uploaded file
     */
    String uploadFile(MultipartFile file, String path) throws IOException;

    /**
     * Uploads raw bytes to R2.
     * 
     * @param content     The byte content
     * @param fileName    The name of the file
     * @param contentType The content type (e.g. image/svg+xml)
     * @param path        The path/prefix within the bucket
     * @return The public URL of the uploaded file
     */
    String uploadBytes(byte[] content, String fileName, String contentType, String path);

    /**
     * Deletes a file from R2.
     * 
     * @param fileUrl The full URL or key of the file
     */
    void deleteFile(String fileUrl);
}
