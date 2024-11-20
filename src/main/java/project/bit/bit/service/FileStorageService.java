package project.bit.bit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class FileStorageService {
    @Value("${MODELS_DIR:/app/models}")
    private String modelsDir;

    @Value("${DATA_DIR:/app/data}")
    private String dataDir;

    public void createDirectories(String modelId) {
        try {
            Path modelPath = Paths.get(modelsDir, modelId);
            Path dataPath = Paths.get(dataDir, modelId);

            Files.createDirectories(modelPath);
            Files.createDirectories(dataPath);

            Files.setPosixFilePermissions(modelPath,
                    PosixFilePermissions.fromString("rwxrwxrwx"));
            Files.setPosixFilePermissions(dataPath,
                    PosixFilePermissions.fromString("rwxrwxrwx"));

            log.info("Created directories: {} and {}", modelPath, dataPath);
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to create directories for model: " + modelId, e);
        }
    }

    public String saveFile(MultipartFile file, String modelId, String filename) {
        try {
            Path filePath = Paths.get(dataDir, modelId, filename);
            file.transferTo(filePath);

            Files.setPosixFilePermissions(filePath,
                    PosixFilePermissions.fromString("rw-rw-rw-"));

            log.info("Saved file: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to save file: " + filename, e);
        }
    }

    public void copyPreviousModel(String previousModelId, String newModelId) {
        try {
            Path source = Paths.get(modelsDir, previousModelId);
            Path target = Paths.get(modelsDir, newModelId);
            Files.walk(source).forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath);
                    }
                } catch (IOException e) {
                    throw new ModelTrainingException("Failed to copy previous model: " + previousModelId, e);
                }
            });

            log.info("Copied previous model from {} to {}", source, target);
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to copy previous model", e);
        }
    }

    public byte[] getModel(String modelId) {
        try {
            Path modelDir = Paths.get(modelsDir, modelId);
            if (!Files.exists(modelDir) || !Files.isDirectory(modelDir)) {
                throw new ModelTrainingException("Model directory does not exist: " + modelId);
            }

            File zipFile = zipDirectory(modelDir);

            byte[] zipBytes = Files.readAllBytes(zipFile.toPath());
            Files.delete(zipFile.toPath());

            return zipBytes;
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to retrieve model: " + modelId, e);
        }
    }

    private File zipDirectory(Path sourceDirPath) throws IOException {
        File zipFile = File.createTempFile(sourceDirPath.getFileName().toString(), ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            File sourceDir = sourceDirPath.toFile();
            zipFilesRecursively(sourceDir, sourceDir.getName(), zos);
        }
        return zipFile;
    }

    private void zipFilesRecursively(File fileToZip, String parentDirectoryName, ZipOutputStream zos) throws IOException {
        if (fileToZip.isDirectory()) {
            for (File file : fileToZip.listFiles()) {
                zipFilesRecursively(file, parentDirectoryName + "/" + file.getName(), zos);
            }
        } else {
            try (FileInputStream fis = new FileInputStream(fileToZip)) {
                ZipEntry zipEntry = new ZipEntry(parentDirectoryName);
                zos.putNextEntry(zipEntry);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) >= 0) {
                    zos.write(buffer, 0, length);
                }
            }
        }
    }
}
