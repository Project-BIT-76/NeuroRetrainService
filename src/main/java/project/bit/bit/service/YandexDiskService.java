package project.bit.bit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class YandexDiskService {

    private final String accessToken;
    private final ObjectMapper objectMapper;
    private static final String API_BASE_URL = "https://cloud-api.yandex.net/v1/disk";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public YandexDiskService(@Value("${yandex.disk.access-token}") String accessToken) {
        this.accessToken = accessToken;
        this.objectMapper = new ObjectMapper();
        log.info("YandexDiskService инициализирован");
    }

    public String uploadFile(String localFilePath, String remoteFilePath) throws IOException {
        log.info("Начало загрузки файла: {} в {}", localFilePath, remoteFilePath);

        File file = validateAndGetFile(localFilePath);
        String uploadUrl = getUploadUrl(remoteFilePath);
        return uploadFileToUrl(file, uploadUrl, remoteFilePath);
    }

    public void createFolder(String folderPath) throws IOException {
        log.info("Создание папки: {}", folderPath);

        String url = API_BASE_URL + "/resources?path=" + URLEncoder.encode(folderPath, StandardCharsets.UTF_8);
        HttpURLConnection connection = createConnection(url, "PUT", false);

        int responseCode = connection.getResponseCode();
        if (responseCode == 201) {
            log.info("Папка успешно создана: {}", folderPath);
        } else if (responseCode == 409) {
            log.debug("Папка уже существует: {}", folderPath);
        } else {
            handleErrorResponse("Ошибка при создании папки", connection);
        }
    }

    public void createFoldersRecursively(String folderPath) throws IOException {
        String[] folders = folderPath.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String folder : folders) {
            if (!folder.isEmpty()) {
                currentPath.append("/").append(folder);
                try {
                    createFolder(currentPath.toString());
                } catch (IOException e) {
                    log.debug("Не удалось создать папку: {}, возможно, она уже существует", currentPath);
                }
            }
        }
    }

    public void uploadDirectory(String localDirPath, String remoteDirPath) throws IOException {
        log.info("Начало загрузки директории: {} в {}", localDirPath, remoteDirPath);

        File directory = new File(localDirPath);
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Указанный путь не является директорией: " + localDirPath);
        }

        createFoldersRecursively(remoteDirPath);

        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            log.warn("Директория пуста: {}", localDirPath);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (File file : files) {
            String newRemotePath = remoteDirPath + "/" + file.getName();
            if (file.isDirectory()) {
                executor.submit(() -> {
                    try {
                        uploadDirectory(file.getAbsolutePath(), newRemotePath);
                    } catch (IOException e) {
                        log.error("Ошибка при загрузке директории: {}", file.getAbsolutePath(), e);
                    }
                });
            } else {
                executor.submit(() -> {
                    try {
                        uploadFile(file.getAbsolutePath(), newRemotePath);
                    } catch (IOException e) {
                        log.error("Ошибка при загрузке файла: {}", file.getAbsolutePath(), e);
                    }
                });
            }
        }
        executor.shutdown();
        log.info("Директория успешно загружена: {}", remoteDirPath);
    }

    public String getPublicLink(String path) throws IOException {
        log.info("Получение публичной ссылки для: {}", path);

        waitForFileAvailability(path);

        String publishUrl = API_BASE_URL + "/resources/publish?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpURLConnection publishConnection = createConnection(publishUrl, "PUT", false);
                int responseCode = publishConnection.getResponseCode();

                if (responseCode == 200 || responseCode == 409) {
                    return getResourceLinkWithRetry(path);
                }

                log.warn("Попытка {} публикации файла не удалась, код ответа: {}", attempt, responseCode);
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано ожидание при публикации файла", e);
            }
        }

        throw new IOException("Не удалось опубликовать файл после " + MAX_RETRIES + " попыток");
    }

    private void waitForFileAvailability(String path) throws IOException {
        String checkUrl = API_BASE_URL + "/resources?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpURLConnection checkConnection = createConnection(checkUrl, "GET", false);
                if (checkConnection.getResponseCode() == 200) {
                    return;
                }
                log.warn("Попытка {} проверки файла не удалась, ожидание...", attempt);
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано ожидание доступности файла", e);
            }
        }

        throw new IOException("Файл недоступен после " + MAX_RETRIES + " попыток проверки");
    }

    private String getResourceLinkWithRetry(String path) throws IOException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String url = API_BASE_URL + "/resources?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                HttpURLConnection connection = createConnection(url, "GET", false);

                if (connection.getResponseCode() == 200) {
                    try (InputStream response = connection.getInputStream()) {
                        JsonNode jsonResponse = objectMapper.readTree(response);
                        if (jsonResponse.has("public_url")) {
                            String publicUrl = jsonResponse.get("public_url").asText();
                            if (publicUrl != null && !publicUrl.isEmpty()) {
                                return publicUrl;
                            }
                        }
                    }
                }

                log.warn("Попытка {} получения публичной ссылки не удалась, ожидание...", attempt);
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано ожидание получения публичной ссылки", e);
            }
        }

        throw new IOException("Не удалось получить публичную ссылку после " + MAX_RETRIES + " попыток");
    }

    private File validateAndGetFile(String localFilePath) throws FileNotFoundException {
        File file = new File(localFilePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Файл не найден: " + localFilePath);
        }
        return file;
    }

    private String getUploadUrl(String remoteFilePath) throws IOException {
        String url = API_BASE_URL + "/resources/upload?path=" + URLEncoder.encode(remoteFilePath, StandardCharsets.UTF_8) + "&overwrite=true";
        HttpURLConnection connection = createConnection(url, "GET", false);

        if (connection.getResponseCode() == 200) {
            try (InputStream response = connection.getInputStream()) {
                JsonNode jsonResponse = objectMapper.readTree(response);
                return jsonResponse.get("href").asText();
            }
        }
        handleErrorResponse("Ошибка при получении URL для загрузки", connection);
        return null;
    }

    private String uploadFileToUrl(File file, String uploadUrl, String remoteFilePath) throws IOException {
        HttpURLConnection uploadConnection = createConnection(uploadUrl, "PUT", true);

        try (OutputStream outputStream = uploadConnection.getOutputStream();
             FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.transferTo(outputStream);
        }

        if (uploadConnection.getResponseCode() == 201) {
            return remoteFilePath;
        }
        handleErrorResponse("Ошибка при загрузке файла", uploadConnection);
        return null;
    }

    private HttpURLConnection createConnection(String url, String method, boolean doOutput) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "OAuth " + accessToken);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        if (doOutput) {
            connection.setDoOutput(true);
        }
        return connection;
    }

    private void handleErrorResponse(String message, HttpURLConnection connection) throws IOException {
        String errorMessage = String.format("%s: Код ответа %d - %s",
                message, connection.getResponseCode(), connection.getResponseMessage());
        log.error(errorMessage);
        throw new IOException(errorMessage);
    }
}