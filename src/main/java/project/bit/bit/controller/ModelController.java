package project.bit.bit.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.bit.bit.exception.ModelTrainingException;
import project.bit.bit.service.ModelTrainingService;

import java.util.Map;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Slf4j
public class ModelController {
    private final ModelTrainingService modelTrainingService;

    @PostMapping("/train")
    public ResponseEntity<?> trainModel(
            @RequestParam Map<String, MultipartFile> files,
            @RequestParam(value = "previousModel", required = false) String previousModelId) {
        try {
            log.info("Received training request with {} files, previous model: {}",
                    files.size(), previousModelId);

            files.forEach((name, file) ->
                    log.info("File: {} ({})", name, file.getOriginalFilename()));

            String modelId = modelTrainingService.trainModel(files, previousModelId);
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

            byte[] zipBytes = modelTrainingService.getModel(modelId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + modelId + ".zip")
                    .body(zipBytes);
        } catch (ModelTrainingException e) {
            log.error("Failed to retrieve model: {}", modelId, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Unexpected error occurred", e);
            return ResponseEntity.status(500).build();
        }
    }
}