package project.bit.bit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class FileStorageService {
    @Value("${storage.models-dir:/app/models}")
    private String modelsDir;

    @Value("${storage.data-dir:/app/data}")
    private String dataDir;

    @Value("${storage.allowed-extensions:csv}")
    private List<String> allowedExtensions;

    public void createDirectories(String modelId) {
        try {
            Path modelPath = Paths.get(modelsDir, modelId);
            Path dataPath = Paths.get(dataDir, modelId);

            Files.createDirectories(modelPath);
            Files.createDirectories(dataPath);

            setDirectoryPermissions(modelPath);
            setDirectoryPermissions(dataPath);

            // Создаем пустой файл-маркер для обозначения, что директория модели существует
            Path markerFile = modelPath.resolve(".model");
            Files.createFile(markerFile);
            setDirectoryPermissions(markerFile);

            log.info("Созданы директории для модели {}: {} и {}", modelId, modelPath, dataPath);
        } catch (IOException e) {
            throw new ModelTrainingException("Ошибка при создании директорий для модели: " + modelId, e);
        }
    }

    private void setDirectoryPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (UnsupportedOperationException e) {
            log.warn("POSIX права доступа не поддерживаются для пути: {}", path);
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
                log.info("Успешно сохранен файл: {} как {}", paramName, filePath);
            } catch (IOException e) {
                throw new ModelTrainingException("Ошибка при сохранении файла: " + paramName, e);
            }
        });

        return savedPaths;
    }

    private void validateFiles(Map<String, MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ModelTrainingException("Не предоставлены файлы для обучения");
        }

        List<String> invalidFiles = files.values().stream()
                .filter(file -> !isValidFileExtension(file.getOriginalFilename()))
                .map(MultipartFile::getOriginalFilename)
                .collect(Collectors.toList());

        if (!invalidFiles.isEmpty()) {
            throw new ModelTrainingException("Обнаружены недопустимые типы файлов: " + String.join(", ", invalidFiles) +
                    ". Разрешенные расширения: " + String.join(", ", allowedExtensions));
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
        Files.createDirectories(destination.getParent());
        file.transferTo(destination);
        try {
            Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-rw-rw-"));
        } catch (UnsupportedOperationException e) {
            log.warn("POSIX права доступа не поддерживаются для файла: {}", destination);
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
            throw new ModelTrainingException("Ошибка при получении модели: " + modelId, e);
        }
    }

    private void validateModelDirectory(Path modelDir) {
        Path markerFile = modelDir.resolve(".model");
        if (!Files.exists(markerFile)) {
            throw new ModelTrainingException("Директория модели не существует или не содержит обученную модель: " + modelDir);
        }
    }

    private File zipDirectory(Path sourceDirPath) throws IOException {
        File zipFile = File.createTempFile(sourceDirPath.getFileName().toString(), ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourceDirPath)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.getFileName().toString().equals(".model"))
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
            throw new ModelTrainingException("Ошибка при добавлении файла в архив: " + path, e);
        }
    }
}