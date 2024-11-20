package project.bit.bit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

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
            Path source = Paths.get(modelsDir, previousModelId, "model.h5");
            Path target = Paths.get(modelsDir, newModelId, "previous_model.h5");
            Files.copy(source, target);

            Files.setPosixFilePermissions(target,
                    PosixFilePermissions.fromString("rw-rw-rw-"));

            log.info("Copied previous model from {} to {}", source, target);
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to copy previous model", e);
        }
    }

    public byte[] getModel(String modelId) {
        try {
            Path modelPath = Paths.get(modelsDir, modelId, "model.h5");
            log.info("Retrieving model from: {}", modelPath);
            return Files.readAllBytes(modelPath);
        } catch (IOException e) {
            throw new ModelTrainingException("Failed to retrieve model: " + modelId, e);
        }
    }
}
