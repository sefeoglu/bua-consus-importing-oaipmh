package io.piveau.importing.oaipmh.responses;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

public class OAIPMHResponse extends HttpResponse<Document> {

	private static final Namespace oaiNamespace = Namespace.getNamespace("oai", "http://www.openarchives.org/OAI/2.0/");

	private Element errorElement;

	public OAIPMHResponse(Document content) {
		super(content);
		errorElement = content.getRootElement().getChild("error", oaiNamespace);
	}

	@Override
	public boolean isError() {
		return errorElement != null;
	}

	@Override
	public boolean isSuccess() {
		return !isError();
	}
	
	@Override
	public OAIPMHError getError() {
		return !isError() ? null : new OAIPMHError(new Document(errorElement.detach()));
	}

	@Override
	public OAIPMHResult getResult() {
		return isError() ? null : new OAIPMHResult(content);
	}

}
