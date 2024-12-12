package project.bit.bit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;
import project.bit.bit.model.Model;
import project.bit.bit.repository.ModelRepository;
import project.bit.bit.service.YandexDiskService;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTrainingService {
    private final FileStorageService fileStorageService;
    private final DockerService dockerService;
    private final ModelRepository modelRepository;
    private final YandexDiskService yandexDiskService;

    @Value("${PYTHON_SCRIPT_PATH:/work_data/peak_hour_train_raw.py}")
    private String pythonScriptPath;

    public String trainModel(Map<String, MultipartFile> files, String previousModelId) {
        Model model = new Model();
        model.setName("Temporary Name");
        model = modelRepository.save(model);

        String modelId = model.getId().toString();
        log.info("Starting model training with ID: {}", modelId);

        try {
            fileStorageService.createDirectories(modelId);

            Map<String, String> filePaths = fileStorageService.saveFiles(files, modelId);

            if (previousModelId != null) {
                fileStorageService.copyPreviousModel(previousModelId, modelId);
            }

            dockerService.executeTraining(modelId, pythonScriptPath, modelId);

            String jsonFilePath = fileStorageService.getModelJsonPath(modelId, "model_description.json");
            Map<String, Object> modelDescription = fileStorageService.readJson(jsonFilePath);

            model.setName("Model_" + modelId);
            model.setInputDescription(modelDescription.getOrDefault("inputDescription", "N/A").toString());
            model.setOutputDescription(modelDescription.getOrDefault("outputDescription", "N/A").toString());

            String localModelDir = "/app/models/" + modelId;

            String remotePath = "models/" + modelId;

            yandexDiskService.uploadDirectory(localModelDir, remotePath);

            String modelClassFileUrl = yandexDiskService.getPublicLink(remotePath + "/saved_model.pb");
            String modelDirUrl = yandexDiskService.getPublicLink(remotePath);

            model.setModelClassFilePath(modelClassFileUrl);
            model.setModelFilePath(modelDirUrl);


            modelRepository.save(model);

            log.info("Model training completed successfully for ID: {}", modelId);
            return modelId;
        } catch (Exception e) {
            log.error("Error during model training for ID: {}", modelId, e);
            throw new ModelTrainingException("Model training failed", e);
        }
    }

    public byte[] getModel(String modelId) {
        return fileStorageService.getModel(modelId);
    }
}