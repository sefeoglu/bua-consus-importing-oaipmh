package io.piveau.importing;

import io.piveau.importing.oaipmh.ImportingOaipmhVerticle;
import io.piveau.pipe.connector.PipeConnector;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Launcher;

import java.util.Arrays;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(io.vertx.core.Future<Void> startFuture) {
        vertx.deployVerticle(ImportingOaipmhVerticle.class, new DeploymentOptions().setWorker(true), result -> {
            if (result.succeeded()) {
                PipeConnector.create(vertx, cr -> {
                    if (cr.succeeded()) {
                        cr.result().consumerAddress(ImportingOaipmhVerticle.ADDRESS);
                        startFuture.complete();
                    } else {
                        startFuture.fail(cr.cause());
                    }
                });
            } else {
                startFuture.fail(result.cause());
            }
        });
    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        Launcher.executeCommand("run", params);
    }

}
