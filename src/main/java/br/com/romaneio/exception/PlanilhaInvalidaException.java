package br.com.romaneio.exception;

public class PlanilhaInvalidaException extends RomaneioException {
    public PlanilhaInvalidaException(String message) {
        super(message);
    }

    public PlanilhaInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
