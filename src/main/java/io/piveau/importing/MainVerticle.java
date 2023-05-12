package io.piveau.importing;

import io.piveau.importing.oaipmh.ImportingOaipmhVerticle;
import io.piveau.pipe.connector.PipeConnector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Launcher;
import io.vertx.core.Promise;

import java.util.Arrays;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.deployVerticle(ImportingOaipmhVerticle.class, new DeploymentOptions().setWorker(true))
                .compose(id -> PipeConnector.create(vertx))
                .onSuccess(connector -> {
                    connector.publishTo(ImportingOaipmhVerticle.ADDRESS);
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        Launcher.executeCommand("run", params);
    }

}
