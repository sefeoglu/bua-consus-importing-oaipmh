package io.piveau.importing.oaipmh.responses;

public abstract class HttpError<T> {
    protected final T error;

    protected HttpError(T error) {
        this.error = error;
    }

    protected abstract String getType();

    protected abstract String getMessage();

}
