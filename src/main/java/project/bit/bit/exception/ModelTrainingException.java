package project.bit.bit.exception;

public class ModelTrainingException extends RuntimeException {
    public ModelTrainingException(String message) {
        super(message);
    }

    public ModelTrainingException(String message, Throwable cause) {
        super(message, cause);
    }
}