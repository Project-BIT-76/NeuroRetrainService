package project.bit.bit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTrainingService {
    private final FileStorageService fileStorageService;
    private final DockerService dockerService;

    @Value("${PYTHON_SCRIPT_PATH:/work_data/peak_hour_train_raw.py}")
    private String pythonScriptPath;

    public String trainModel(
            MultipartFile trainHoliday,
            MultipartFile trainPphour,
            MultipartFile trainX,
            MultipartFile trainYCl,
            String previousModelId) {
        String modelId = UUID.randomUUID().toString();
        log.info("Starting model training with ID: {}", modelId);

        try {
            fileStorageService.createDirectories(modelId);

            String holidayPath = fileStorageService.saveFile(trainHoliday, modelId, "train_holiday.csv");
            String pphourPath = fileStorageService.saveFile(trainPphour, modelId, "train_pphour.csv");
            String xPath = fileStorageService.saveFile(trainX, modelId, "train_x.csv");
            String yClPath = fileStorageService.saveFile(trainYCl, modelId, "train_y_cl.csv");

            if (previousModelId != null) {
                fileStorageService.copyPreviousModel(previousModelId, modelId);
            }

            dockerService.executeTraining(modelId, pythonScriptPath, modelId);

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