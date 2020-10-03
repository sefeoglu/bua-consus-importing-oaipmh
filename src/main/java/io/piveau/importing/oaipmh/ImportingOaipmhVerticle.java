package io.piveau.importing.oaipmh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.piveau.importing.oaipmh.responses.OAIPMHResponse;
import io.piveau.importing.oaipmh.responses.OAIPMHResult;
import io.piveau.pipe.PipeContext;
import io.piveau.rdf.PreProcessing;
import io.piveau.utils.JenaUtils;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import kotlin.Pair;
import org.apache.jena.rdf.model.Model;
import org.jdom2.*;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class ImportingOaipmhVerticle extends AbstractVerticle {

    private static final Namespace oaiNamespace = Namespace.getNamespace("oai", "http://www.openarchives.org/OAI/2.0/");

    private static final String XML_PATH_OAIPMH_RECORD_IDENTIFIER = "/oai:record/oai:header/oai:identifier/text()";
    private static final String XML_PATH_OAIPMH_RECORD_METADATA = "/oai:record/oai:metadata/*[1]";

    public static final String ADDRESS = "io.piveau.pipe.importing.oaipmh.queue";

    private WebClient client;

    private CircuitBreaker breaker;

    private int defaultDelay;
    private String defaultOaiPmhAdapterUri;

    private static final List<String> dcatFormats = List.of("dcat_ap", "dcat");

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().consumer(ADDRESS, this::handlePipe);
        client = WebClient.create(vertx);

        breaker = CircuitBreaker.create("oaipmh-breaker", vertx, new CircuitBreakerOptions().setMaxRetries(2).setTimeout(200000))
                .retryPolicy(count -> count * 2000L);

        ConfigStoreOptions envStoreOptions = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject().put("keys", new JsonArray()
                        .add("PIVEAU_IMPORTING_SEND_LIST_DELAY")
                        .add("PIVEAU_OAIPMH_ADAPTER_URI")));
        ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(envStoreOptions));
        retriever.getConfig(ar -> {
            if (ar.succeeded()) {
                defaultDelay = ar.result().getInteger("PIVEAU_IMPORTING_SEND_LIST_DELAY", 8000);
                defaultOaiPmhAdapterUri = ar.result().getString("PIVEAU_OAIPMH_ADAPTER_URI");
                startPromise.complete();
            } else {
                startPromise.fail(ar.cause());
            }
        });
        retriever.listen(change -> {
            defaultDelay = change.getNewConfiguration().getInteger("PIVEAU_IMPORTING_SEND_LIST_DELAY", 8000);
        });
    }

    private void handlePipe(Message<PipeContext> message) {
        PipeContext pipeContext = message.body();
        pipeContext.log().info("Import started.");

        fetch(null, pipeContext, new ArrayList<>());
    }

    private void fetch(String resumptionToken, PipeContext pipeContext, List<String> identifiers) {

        JsonObject config = pipeContext.getConfig();

        String tmp = config.getString("address", defaultOaiPmhAdapterUri);
        if (config.containsKey("resource")) {
            tmp += "/" + config.getString("resource");
        }
        final String address = tmp;

        HttpRequest<Buffer> request = client.getAbs(address);

        String metadata = config.getString("metadata", "dcat_ap");
        if (!request.queryParams().contains("metadataPrefix")) {
            request.addQueryParam("metadataPrefix", metadata);
        }

        if (config.containsKey("queries")) {
            JsonObject queries = config.getJsonObject("queries");
            queries.getMap().forEach((key, value) -> request.setQueryParam(key, value.toString()));
        }

        if (!request.queryParams().contains("verb")) {
            request.addQueryParam("verb", "ListRecords");
        }

        if (resumptionToken != null) {
            request.addQueryParam("resumptionToken", resumptionToken);
        }

        breaker.<HttpResponse<Buffer>>execute(fut -> request.send(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                if (response.statusCode() == 200) {
                    fut.complete(ar.result());
                } else {
                    pipeContext.log().warn("{} - {}", response.statusMessage(), response.bodyAsString());
                    fut.fail(response.statusMessage() + "\n" + response.bodyAsString());
                }
            } else {
                pipeContext.log().error("Sent metadata request", ar.cause());
                fut.fail(ar.cause());
            }
        })).onComplete(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                ByteArrayInputStream stream = new ByteArrayInputStream(response.bodyAsString().getBytes());
                try {
                    Document document = new SAXBuilder().build(stream);

                    OAIPMHResponse oaipmhResponse = new OAIPMHResponse(document);
                    if (oaipmhResponse.isSuccess()) {
                        String outputFormat = config.getString("outputFormat", "application/n-triples");

                        XPathFactory xpFactory = XPathFactory.instance();
                        XPathExpression<Text> identifierExpression = xpFactory.compile(XML_PATH_OAIPMH_RECORD_IDENTIFIER, Filters.text(), Collections.emptyMap(), oaiNamespace);
                        XPathExpression<Element> metadataExpression = xpFactory.compile(XML_PATH_OAIPMH_RECORD_METADATA, Filters.element(), Collections.emptyMap(), oaiNamespace);

                        OAIPMHResult result = oaipmhResponse.getResult();
                        List<Document> records = result.getRecords();
                        records.forEach(doc -> {

                            Text identifier = identifierExpression.evaluateFirst(doc);
                            Element dataset = metadataExpression.evaluateFirst(doc);

                            String output = new XMLOutputter(Format.getPrettyFormat()).outputString(dataset);

                            if (identifier != null) {
                                if (identifiers.contains(identifier.getTextTrim())) {
                                    pipeContext.log().warn("Identifier duplication: {}", identifier.getTextTrim());
                                }
                                identifiers.add(identifier.getTextTrim());
                                ObjectNode dataInfo = new ObjectMapper().createObjectNode()
                                        .put("total", result.completeSize() != -1 ? result.completeSize() : records.size())
                                        .put("counter", identifiers.size())
                                        .put("identifier", identifier.getTextTrim())
                                        .put("catalogue", config.getString("catalogue"));

                                if (dcatFormats.contains(metadata)) {
//                                    output = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
//                                            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" >\n" +
//                                            output +
//                                            "\n</rdf:RDF>";

                                    Pair<ByteArrayOutputStream, String> parsed = PreProcessing.preProcess(output.getBytes(), "application/rdf+xml", address);
                                    output = parsed.getFirst().toString();
                                    try {
                                        Model m = JenaUtils.read(output.getBytes(), parsed.getSecond(), address);
                                        output = JenaUtils.write(m, outputFormat);
                                    } catch (Exception e) {
                                        pipeContext.log().error("Normalize model", e);
                                    }
                                }
                                pipeContext.setResult(output, outputFormat, dataInfo).forward();
                                pipeContext.log().info("Data imported: {}", dataInfo.toString());
                                pipeContext.log().debug("Data content: {}", output);
                            } else {
                                pipeContext.log().error("No identifier: {}", output);
                            }
                        });
                        if (result.token() != null && !result.token().isEmpty()) {
                            fetch(result.token(), pipeContext, identifiers);
                        } else {
                            pipeContext.log().info("Import metadata finished");
                            int delay = pipeContext.getConfig().getInteger("sendListDelay", defaultDelay);
                            vertx.setTimer(delay, t -> {
                                ObjectNode info = new ObjectMapper().createObjectNode()
                                        .put("content", "identifierList")
                                        .put("catalogue", config.getString("catalogue"));
                                pipeContext.setResult(new JsonArray(identifiers).encodePrettily(), "application/json", info).forward();
                            });
                        }
                    } else {
                        pipeContext.setFailure(oaipmhResponse.getError().getMessage());
                    }
                } catch (Exception e) {
                    pipeContext.setFailure(e);
                }
            } else {
                pipeContext.setFailure(ar.cause());
            }
        });

    }

}
