package com.ardikars.vertx.config.yaml;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class YamlProcessorTest {

    public static void main(String[] args) {
        final Vertx vertx = Vertx.vertx();
        final ConfigStoreOptions store = new ConfigStoreOptions() //
                .setType("file") //
                .setFormat("yaml") //
                .setConfig(new JsonObject() //
                        .put("path", "src/test/resources/config.yaml") //
                ); //
        final ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions() //
                .addStore(store) //
        ); //
        retriever //
                .getConfig() //
                .onSuccess(entries -> {
                    System.out.println(entries.encodePrettily());
                    vertx.close();
                }) //
                .onFailure(ex -> {
                    System.out.println(ex.getMessage());
                    vertx.close();
                });
    }
}
