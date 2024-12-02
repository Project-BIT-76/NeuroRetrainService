package project.bit.bit.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ModelNameGenerator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public String generateName() {
        return "model_" + LocalDateTime.now().format(FORMATTER);
    }

    public String generateDescription(Map<String, MultipartFile> files) {
        return "Model trained on files: " +
                files.values().stream()
                        .map(MultipartFile::getOriginalFilename)
                        .collect(Collectors.joining(", "));
    }
}