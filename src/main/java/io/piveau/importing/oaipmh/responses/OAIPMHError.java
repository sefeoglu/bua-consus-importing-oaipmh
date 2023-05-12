package io.piveau.importing.oaipmh.responses;

import org.jdom2.Document;

public class OAIPMHError extends HttpError<Document> {

	public OAIPMHError(Document error) {
		super(error);
	}

	@Override
	public String getType() {
		return error.getRootElement().getAttributeValue("code");
	}

	@Override
	public String getMessage() {
		return error.getRootElement().getTextTrim();
	}
	
}
