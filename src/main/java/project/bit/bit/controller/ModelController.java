package project.bit.bit.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.dto.ModelResponse;
import project.bit.bit.exception.ModelNotFoundException;
import project.bit.bit.exception.ModelTrainingException;
import project.bit.bit.service.ModelService;
import project.bit.bit.service.ModelTrainingService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Slf4j
public class ModelController {
    private final ModelService modelService;
    private final ModelTrainingService modelTrainingService;

    @GetMapping
    public ResponseEntity<?> getAllModels() {
        try {
            log.info("Getting all models");
            List<ModelResponse> models = modelService.getAllModels();
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            log.error("Error retrieving models", e);
            return ResponseEntity.internalServerError()
                    .body("Error retrieving models: " + e.getMessage());
        }
    }

    @GetMapping("/{modelId}")
    public ResponseEntity<?> getModel(@PathVariable UUID modelId) {
        try {
            log.info("Getting model with ID: {}", modelId);
            ModelResponse model = modelService.getModelById(modelId);
            return ResponseEntity.ok(model);
        } catch (ModelNotFoundException e) {
            log.warn("Model not found: {}", modelId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving model: {}", modelId, e);
            return ResponseEntity.internalServerError()
                    .body("Error retrieving model: " + e.getMessage());
        }
    }

    @PostMapping("/train")
    public ResponseEntity<?> trainModel(@RequestParam Map<String, MultipartFile> files) {
        try {
            log.info("Starting model training with {} files", files.size());
            ModelResponse model = modelTrainingService.trainModel(files);
            return ResponseEntity.ok(model);
        } catch (Exception e) {
            log.error("Error during model training", e);
            return ResponseEntity.internalServerError()
                    .body("Error during model training: " + e.getMessage());
        }

    }

    @GetMapping("/{modelId}/download")
    public ResponseEntity<?> downloadModel(@PathVariable UUID modelId) {
        try {
            log.info("Downloading model with ID: {}", modelId);
            byte[] modelData = modelTrainingService.getModelData(modelId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + modelId + ".zip")
                    .body(modelData);
        } catch (ModelNotFoundException e) {
            log.warn("Model not found: {}", modelId);
            return ResponseEntity.notFound().build();
        } catch (ModelTrainingException e) {
            log.error("Error downloading model: {}", modelId, e);
            return ResponseEntity.badRequest().body("Error downloading model: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error downloading model: {}", modelId, e);
            return ResponseEntity.internalServerError()
                    .body("Unexpected error downloading model: " + e.getMessage());
        }
    }
}