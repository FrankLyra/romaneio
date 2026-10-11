package br.com.romaneio.exception;

public class RomaneioException extends RuntimeException {
    public RomaneioException(String message) {
        super(message);
    }

    public RomaneioException(String message, Throwable cause) {
        super(message, cause);
    }
}
