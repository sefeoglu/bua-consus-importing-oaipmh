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

    private static final List<String> dcatFormats = List.of("dc_ap", "dc");

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.eventBus().consumer(ADDRESS, this::handlePipe);
        client = WebClient.create(vertx);

        breaker = CircuitBreaker.create("oaipmh-breaker", vertx, new CircuitBreakerOptions().setMaxRetries(2).setTimeout(200000))
                .retryPolicy(count -> count * 2000L);

        ConfigStoreOptions envStoreOptions = new ConfigStoreOptions()
                .setType("env");
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
        retriever.listen(change ->
            defaultDelay = change.getNewConfiguration().getInteger("PIVEAU_IMPORTING_SEND_LIST_DELAY", 8000)
        );
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

        String metadataPrefix = config.getString("metadata", "dc_ap");
        if (!request.queryParams().contains("metadataPrefix")) {
            request.addQueryParam("metadataPrefix", metadataPrefix);
        } else {
            metadataPrefix = request.queryParams().get("metadataPrefix");
        }
        final String metadata = metadataPrefix;

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

        breaker.<HttpResponse<Buffer>>execute(promise -> request.send()
                        .onSuccess(response -> {
                            if (response.statusCode() == 200) {
                                promise.complete(response);
                            } else {
                                pipeContext.log().warn("{} - {}", response.statusMessage(), response.bodyAsString());
                                promise.fail(response.statusMessage() + "\n" + response.bodyAsString());
                            }
                        })
                        .onFailure(cause -> {
                            pipeContext.log().error("Sent metadata request", cause);
                            promise.fail(cause);
                        })
                )
                .onSuccess(response -> {
                    ByteArrayInputStream stream = new ByteArrayInputStream(response.body().getBytes());
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

                                if (pipeContext.log().isDebugEnabled()) {
                                    pipeContext.log().debug(new XMLOutputter(Format.getPrettyFormat()).outputString(doc));
                                }

                                Text identifier = identifierExpression.evaluateFirst(doc);
                                Element dataset = metadataExpression.evaluateFirst(doc);
                                if (dataset != null && identifier != null) {
                                    String output = new XMLOutputter(Format.getPrettyFormat()).outputString(dataset);

                                    if (identifiers.contains(identifier.getTextTrim())) {
                                        pipeContext.log().warn("Identifier duplication: {}", identifier.getTextTrim());
                                    }

                                    identifiers.add(identifier.getTextTrim());
                                    ObjectNode dataInfo = new ObjectMapper().createObjectNode()
                                            .put("total", result.completeSize() != -1 ? result.completeSize() : records.size())
                                            .put("counter", identifiers.size())
                                            .put("identifier", identifier.getTextTrim())
                                            .put("catalogue", config.getString("catalogue"));

                                    String format = "application/rdf+xml";
                                    if (dcatFormats.contains(metadata) && config.getBoolean("preProcessing", false)) {
                                        try {
                                            Pair<ByteArrayOutputStream, String> parsed = PreProcessing.preProcess(output.getBytes(), "application/rdf+xml", address);
                                            byte[] outputBytes = parsed.getFirst().toByteArray();
                                            Model m = JenaUtils.read(outputBytes, parsed.getSecond(), address);
                                            parsed.getFirst().close();
                                            output = JenaUtils.write(m, outputFormat);
                                            format = outputFormat;
                                        } catch (Exception e) {
                                            pipeContext.log().error("Normalize model ({})", identifier, e);
                                        }
                                    }
                                    pipeContext.setResult(output, format, dataInfo).forward();
                                    pipeContext.log().info("Data imported: {}", dataInfo.toString());
                                    pipeContext.log().debug("Data content: {}", output);
                                } else {
                                    pipeContext.log().error("No dataset or identifier");
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
                })
                .onFailure(pipeContext::setFailure);
    }

}
