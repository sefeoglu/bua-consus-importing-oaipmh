package io.piveau.importing.oaipmh.responses;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OAIPMHResult extends HttpResult<Document> {

	private static final Namespace oaiNamespace = Namespace.getNamespace("oai", "http://www.openarchives.org/OAI/2.0/");

	/** XPath expressions **/
	private static final String XML_PATH_OAIPMH_LIST_RECORDS = "/oai:OAI-PMH/oai:ListRecords/oai:record";
	private static final String XML_PATH_OAIPMH_LIST_IDENTIFIERS = "/oai:OAI-PMH/oai:ListIdentifiers/oai:header/oai:identifier/text()";
	private static final String XML_PATH_OAIPMH_RESUMPTION_TOKEN = "/oai:OAI-PMH/oai:ListSets/oai:resumptionToken|/oai:OAI-PMH/oai:ListRecords/oai:resumptionToken|/oai:OAI-PMH/oai:ListIdentifiers/oai:resumptionToken";
	private static final String XML_PATH_OAIPMH_LIST_FORMATS = "/oai:OAI-PMH/oai:ListMetadataFormats/oai:metadataFormat/oai:metadataPrefix/text()";

	private static final XPathExpression<Element> token;
	private static final XPathExpression<Text> identifiers;
	private static final XPathExpression<Element> records;
	private static final XPathExpression<Text> formats;

	private String verb;

	static {
		XPathFactory xpFactory = XPathFactory.instance();
		token = xpFactory.compile(XML_PATH_OAIPMH_RESUMPTION_TOKEN, Filters.element(), Collections.emptyMap(), oaiNamespace);
		records = xpFactory.compile(XML_PATH_OAIPMH_LIST_RECORDS, Filters.element(), Collections.emptyMap(), oaiNamespace);
		identifiers = xpFactory.compile(XML_PATH_OAIPMH_LIST_IDENTIFIERS, Filters.text(), Collections.emptyMap(), oaiNamespace);
		formats = xpFactory.compile(XML_PATH_OAIPMH_LIST_FORMATS, Filters.text(), Collections.emptyMap(), oaiNamespace);
	}

	public OAIPMHResult(Document result) {
		super(result);
		verb = result.getRootElement().getChild("request", oaiNamespace).getAttributeValue("verb");
	}

	@Override
	public Document getContent() {
		return result;
	}

	public List<Document> getRecords() {
		List<Element> elements = records.evaluate(result);
		List<Document> records = new ArrayList<>(elements.size());

		records.addAll(elements.stream().map(next -> new Document(next.detach())).collect(Collectors.toList()));
		return records;
	}

	public List<String> getIdentifiers() {
		return identifiers.evaluate(result).stream().map(next -> next.getText()).collect(Collectors.toList());
	}

	public List<String> getFormats() {
		return formats.evaluate(result).stream().map(next -> next.getText()).collect(Collectors.toList());
	}

	public String token() {
		Element element = token.evaluateFirst(result);
		return element != null ? element.getTextTrim() : null;
	}

	public int completeSize() {
		Element element = token.evaluateFirst(result);
		return element != null ? Integer.parseInt(element.getAttributeValue("completeListSize")) : -1;
	}

}
