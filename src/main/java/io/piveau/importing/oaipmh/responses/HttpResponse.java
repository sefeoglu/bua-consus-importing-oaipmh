package io.piveau.importing.oaipmh.responses;

public abstract class HttpResponse<T> {

    protected final T content;

    protected HttpResponse(T content) {
    	this.content = content;
    }

    protected abstract boolean isError();

    protected abstract boolean isSuccess();

    protected abstract HttpResult<T> getResult();

    protected abstract HttpError<T> getError();

}
