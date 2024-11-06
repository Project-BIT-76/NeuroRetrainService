package project.bit.bit.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.bit.bit.exception.ModelTrainingException;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class DockerService {
    private static final String TENSORFLOW_CONTAINER = "tensorflow";
    private final DockerClient dockerClient;

    public void executeTraining(String modelId, String pythonFilePath, String dataFilePath) {
        try {
            log.info("Starting training for model: {} with python file: {} and data file: {}",
                    modelId, pythonFilePath, dataFilePath);

            String containerDataPath = dataFilePath.replace("/app/data", "/data");
            String containerModelPath = "/models/" + modelId + "/model.h5";

            String[] command = {
                    "python",
                    pythonFilePath,
                    "--data", containerDataPath,
                    "--model", containerModelPath
            };

            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(TENSORFLOW_CONTAINER)
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion(12, TimeUnit.HOURS);

            log.info("Training stdout: {}", stdout.toString());
            if (stderr.size() > 0) {
                log.error("Training stderr: {}", stderr.toString());
            }

            Integer exitCode = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec().getExitCode();
            if (exitCode != null && exitCode != 0) {
                throw new ModelTrainingException("Training process failed with exit code: " + exitCode +
                        "\nStderr: " + stderr.toString());
            }

            log.info("Training completed successfully for model: {}", modelId);
        } catch (Exception e) {
            log.error("Failed to execute training in Docker container", e);
            throw new ModelTrainingException("Failed to execute training in Docker container: " + e.getMessage(), e);
        }
    }
}