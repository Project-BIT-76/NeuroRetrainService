package project.bit.bit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTrainingService {
    private final FileStorageService fileStorageService;
    private final DockerService dockerService;

    @Value("${PYTHON_SCRIPT_PATH:/data/train.py}")
    private String pythonScriptPath;

    public String trainModel(MultipartFile trainingData, String previousModelId) {
        String modelId = UUID.randomUUID().toString();
        log.info("Starting model training with ID: {}", modelId);

        try {
            fileStorageService.createDirectories(modelId);

            String dataFilePath = fileStorageService.saveFile(trainingData, modelId, "data.csv");

            if (previousModelId != null) {
                fileStorageService.copyPreviousModel(previousModelId, modelId);
            }

            dockerService.executeTraining(modelId, pythonScriptPath, dataFilePath);

            log.info("Model training completed successfully for ID: {}", modelId);
            return modelId;
        } catch (Exception e) {
            log.error("Error during model training for ID: {}", modelId, e);
            throw e;
        }
    }

    public byte[] getModel(String modelId) {
        return fileStorageService.getModel(modelId);
    }
}