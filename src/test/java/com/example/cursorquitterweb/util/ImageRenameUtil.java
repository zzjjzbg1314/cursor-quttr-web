package com.example.cursorquitterweb.util;

import org.assertj.core.util.Sets;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ImageRenameUtil {

    // 支持的图片文件扩展名
    private static final Set<String> IMAGE_EXTENSIONS = Sets.newTreeSet(".jpg", ".jpeg", ".png", ".gif", ".bmp");

    public static void renameImagesSequentially(String directoryPath) {
        Path dir = Paths.get(directoryPath);

        try {
            // 获取目录中的所有图片文件并按修改时间排序（保持原始顺序）
            List<Path> imageFiles = Files.walk(dir, 1)
                    .filter(Files::isRegularFile)
                    .filter(path -> isImageFile(path.getFileName().toString()))
                    .collect(Collectors.toList());

            System.out.println("找到 " + imageFiles.size() + " 个图片文件");

            // 按顺序重命名文件
            int count = 1;
            for (Path imageFile : imageFiles) {
                String originalExtension = getFileExtension(imageFile.getFileName().toString());
                String newFileName = count + originalExtension;
                Path newFilePath = dir.resolve(newFileName);

                try {
                    // 如果目标文件已存在，先删除
                    if (Files.exists(newFilePath)) {
                        Files.delete(newFilePath);
                    }

                    Files.move(imageFile, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("重命名: " + imageFile.getFileName() + " -> " + newFileName);
                    count++;

                } catch (IOException e) {
                    System.err.println("重命名文件失败: " + imageFile + " - " + e.getMessage());
                }
            }

            System.out.println("完成! 共重命名了 " + (count - 1) + " 个文件");

        } catch (IOException e) {
            System.err.println("读取目录失败: " + e.getMessage());
        }
    }

    // 检查文件是否为图片文件
    private static boolean isImageFile(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return IMAGE_EXTENSIONS.contains(extension);
    }

    // 获取文件扩展名
    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return "";
    }

    // 使用方法
    public static void main(String[] args) {
        String directoryPath = "/Users/zongjie/Downloads/精选图片/";
        renameImagesSequentially(directoryPath);
    }
}