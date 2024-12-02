package project.bit.bit.util;

import org.springframework.stereotype.Component;
import project.bit.bit.config.StorageProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class PathUtils {
    private final StorageProperties storageProperties;

    public PathUtils(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public Path getModelDir(String modelId) {
        return Paths.get(storageProperties.getModelsDir(), modelId);
    }

    public Path getDataDir(String modelId) {
        return Paths.get(storageProperties.getDataDir(), modelId);
    }

    public Path getModelClassPath(String modelId) {
        return getModelDir(modelId).resolve("-");
    }

    public Path getModelFilePath(String modelId) {
        return getModelDir(modelId).resolve("model.keras");
    }

    public Path getMarkerFilePath(String modelId) {
        return getModelDir(modelId).resolve(".model");
    }

    public String getRelativePath(Path basePath, Path fullPath) {
        return basePath.relativize(fullPath).toString();
    }
}