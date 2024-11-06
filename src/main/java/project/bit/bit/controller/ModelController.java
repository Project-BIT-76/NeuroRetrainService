package project.bit.bit.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;
import project.bit.bit.service.ModelTrainingService;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Slf4j
public class ModelController {
    private final ModelTrainingService modelTrainingService;

    @PostMapping("/train")
    public ResponseEntity<?> trainModel(
            @RequestParam("trainingData") MultipartFile trainingData,
            @RequestParam(value = "previousModel", required = false) String previousModelId) {
        try {
            log.info("Received training request with data file: {}, previous model: {}",
                    trainingData.getOriginalFilename(),
                    previousModelId);

            String modelId = modelTrainingService.trainModel(trainingData, previousModelId);
            return ResponseEntity.ok().body(modelId);
        } catch (ModelTrainingException e) {
            log.error("Training failed", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{modelId}")
    public ResponseEntity<?> getModel(@PathVariable String modelId) {
        try {
            log.info("Retrieving model: {}", modelId);
            byte[] modelFile = modelTrainingService.getModel(modelId);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=model.h5")
                    .body(modelFile);
        } catch (ModelTrainingException e) {
            log.error("Failed to retrieve model: {}", modelId, e);
            return ResponseEntity.notFound().build();
        }
    }
}