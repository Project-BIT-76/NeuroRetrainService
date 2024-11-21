package project.bit.bit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class FileStorageService {
    @Value("${MODELS_DIR:/app/models}")
    private String modelsDir;

    @Value("${DATA_DIR:/app/data}")
    private String dataDir;

    @Value("${allowed.file.extensions:csv,txt,json}")
    private List<String> allowedExtensions;

    public void createDirectories(String modelId) {
        try {
            Path modelPath = Paths.get(modelsDir, modelId);
            Path dataPath = Paths.get(dataDir, modelId);

            Files.createDirectories(modelPath);
            Files.createDirectories(dataPath);

            setDirectoryPermissions(modelPath);
            setDirectoryPermissions(dataPath);

            log.info("Created directories for model {}: {} and {}", modelId, modelPath, dataPath);
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to create directories for model: " + modelId, e);
        }
    }

    private void setDirectoryPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (UnsupportedOperationException e) {
            log.warn("POSIX file permissions not supported on this system for path: {}", path);
        }
    }

    public Map<String, String> saveFiles(Map<String, MultipartFile> files, String modelId) {
        validateFiles(files);
        Map<String, String> savedPaths = new HashMap<>();

        files.forEach((paramName, file) -> {
            try {
                String filename = getValidFileName(file, paramName);
                Path filePath = Paths.get(dataDir, modelId, filename);
                saveFile(file, filePath);
                savedPaths.put(paramName, filePath.toString());
                log.info("Successfully saved file: {} as {}", paramName, filePath);
            } catch (IOException e) {
                throw new ModelTrainingException("Failed to save file: " + paramName, e);
            }
        });

        return savedPaths;
    }

    private void validateFiles(Map<String, MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ModelTrainingException("No files provided for training");
        }

        List<String> invalidFiles = files.values().stream()
                .filter(file -> !isValidFileExtension(file.getOriginalFilename()))
                .map(MultipartFile::getOriginalFilename)
                .collect(Collectors.toList());

        if (!invalidFiles.isEmpty()) {
            throw new ModelTrainingException("Invalid file types detected: " + String.join(", ", invalidFiles) +
                    ". Allowed extensions: " + String.join(", ", allowedExtensions));
        }
    }

    private boolean isValidFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) return false;
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return allowedExtensions.contains(extension);
    }

    private String getValidFileName(MultipartFile file, String defaultName) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return defaultName + ".csv";
        }
        return sanitizeFileName(originalFilename);
    }

    private String sanitizeFileName(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private void saveFile(MultipartFile file, Path destination) throws IOException {
        file.transferTo(destination);
        try {
            Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-rw-rw-"));
        } catch (UnsupportedOperationException e) {
            log.warn("POSIX file permissions not supported for file: {}", destination);
        }
    }

    public void copyPreviousModel(String previousModelId, String newModelId) {
        Path source = Paths.get(modelsDir, previousModelId);
        Path target = Paths.get(modelsDir, newModelId);

        if (!Files.exists(source)) {
            throw new ModelTrainingException("Previous model not found: " + previousModelId);
        }

        try {
            Files.walk(source)
                    .forEach(sourcePath -> copyModelFile(sourcePath, source, target));
            log.info("Successfully copied previous model from {} to {}", source, target);
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to copy previous model: " + previousModelId, e);
        }
    }

    private void copyModelFile(Path sourcePath, Path sourceRoot, Path targetRoot) {
        try {
            Path targetPath = targetRoot.resolve(sourceRoot.relativize(sourcePath));
            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(targetPath);
            } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to copy file: " + sourcePath, e);
        }
    }

    public byte[] getModel(String modelId) {
        Path modelDir = Paths.get(modelsDir, modelId);
        validateModelDirectory(modelDir);

        try {
            File zipFile = zipDirectory(modelDir);
            byte[] zipBytes = Files.readAllBytes(zipFile.toPath());
            Files.deleteIfExists(zipFile.toPath());
            return zipBytes;
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to retrieve model: " + modelId, e);
        }
    }

    private void validateModelDirectory(Path modelDir) {
        if (!Files.exists(modelDir)) {
            throw new ModelTrainingException("Model directory does not exist: " + modelDir);
        }
        if (!Files.isDirectory(modelDir)) {
            throw new ModelTrainingException("Model path is not a directory: " + modelDir);
        }
    }

    private File zipDirectory(Path sourceDirPath) throws IOException {
        File zipFile = File.createTempFile(sourceDirPath.getFileName().toString(), ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourceDirPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> addToZip(path, sourceDirPath, zos));
        }
        return zipFile;
    }

    private void addToZip(Path path, Path sourceDir, ZipOutputStream zos) {
        try {
            String zipEntry = sourceDir.relativize(path).toString();
            zos.putNextEntry(new ZipEntry(zipEntry));
            Files.copy(path, zos);
            zos.closeEntry();
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to add file to zip: " + path, e);
        }
    }
}