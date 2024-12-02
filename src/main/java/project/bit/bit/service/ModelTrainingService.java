package project.bit.bit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.dto.ModelResponse;
import project.bit.bit.model.Model;
import project.bit.bit.model.ModelFileType;
import project.bit.bit.util.ModelNameGenerator;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelTrainingService {
    private final FileStorageService fileStorageService;
    private final DockerService dockerService;
    private final ModelService modelService;
    private final ModelNameGenerator modelNameGenerator;

    @Value("${storage.python-script-path:/work_data/peak_hour_train_raw.py}")
    private String pythonScriptPath;

    @Transactional
    public ModelResponse trainModel(Map<String, MultipartFile> files) {
        String modelName = modelNameGenerator.generateName();
        String description = modelNameGenerator.generateDescription(files);

        log.info("Starting model training. Generated name: {}", modelName);

        Model model = modelService.createModel(modelName, description, "Auto-generated model from uploaded files");
        String modelId = model.getId().toString();

        try {
            fileStorageService.createDirectories(modelId);
            Map<String, String> filePaths = fileStorageService.saveFiles(files, modelId);

            // Save file information
            filePaths.forEach((fileName, filePath) ->
                    modelService.addModelFile(model, fileName, filePath, ModelFileType.ASSETS));

            // Execute training
            dockerService.executeTraining(modelId, pythonScriptPath, modelId);

            log.info("Model training completed successfully for ID: {}", modelId);
            return modelService.getModelById(model.getId());
        } catch (Exception e) {
            log.error("Error during model training for ID: {}", modelId, e);
            throw e;
        }
    }

    public byte[] getModelData(UUID modelId) {
        log.debug("Retrieving model data for ID: {}", modelId);
        return fileStorageService.getModel(modelId.toString());
    }
}