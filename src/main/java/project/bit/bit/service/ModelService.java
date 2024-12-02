package project.bit.bit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bit.bit.dto.ModelResponse;
import project.bit.bit.exception.ModelNotFoundException;
import project.bit.bit.model.Model;
import project.bit.bit.model.ModelFile;
import project.bit.bit.model.ModelFileType;
import project.bit.bit.repository.ModelRepository;
import project.bit.bit.util.PathUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModelService {
    private final ModelRepository modelRepository;
    private final PathUtils pathUtils;

    @Transactional(readOnly = true)
    public List<ModelResponse> getAllModels() {
        log.debug("Получение всех моделей из базы данных");
        List<Model> models = modelRepository.findAllWithFiles();
        return models.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ModelResponse getModelById(UUID id) {
        log.debug("Получение модели с ID: {}", id);
        return modelRepository.findByIdWithFiles(id)
                .map(this::convertToResponse)
                .orElseThrow(() -> new ModelNotFoundException("Модель не найдена с ID: " + id));
    }

    @Transactional
    public Model createModel(String name, String inputDescription, String outputDescription) {
        log.debug("Создание новой модели с именем: {}", name);
        Model model = new Model();
        model.setName(name);
        model.setInputDescription(inputDescription);
        model.setOutputDescription(outputDescription);

        // Установка путей к файлам модели
        String modelId = UUID.randomUUID().toString();
        Path modelClassPath = pathUtils.getModelClassPath(modelId);
        Path modelFilePath = pathUtils.getModelFilePath(modelId);

        model.setModelClassFilePath(modelClassPath.toString());
        model.setModelFilePath(modelFilePath.toString());

        return modelRepository.save(model);
    }

    @Transactional
    public void addModelFile(Model model, String name, String filePath, ModelFileType fileType) {
        log.debug("Добавление файла {} к модели {}", name, model.getId());
        ModelFile file = new ModelFile();
        file.setName(name);
        file.setFilePath(filePath);
        file.setFileType(fileType);
        model.addFile(file);
        modelRepository.save(model);
    }

    private ModelResponse convertToResponse(Model model) {
        ModelResponse response = new ModelResponse();
        response.setId(model.getId());
        response.setName(model.getName());
        response.setInputDescription(model.getInputDescription());
        response.setOutputDescription(model.getOutputDescription());
        response.setCreatedAt(model.getCreatedAt());
        response.setFiles(model.getFiles());
        return response;
    }
}