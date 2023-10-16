## Vertx Config YAML

### Usage

```xml
<dependency>
  <groupId>com.ardikars.vertx.config</groupId>
  <artifactId>vertx-config-yaml</artifactId>
  <version>${vertx.config.yaml.version}</version>
</dependency>
```
```java
final ConfigStoreOptions store = new ConfigStoreOptions()
        .setType("file")
        .setFormat("yaml")
        .setConfig(new JsonObject()
                .put("path", "config.yaml")
        );
final ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions()
        .addStore(store)
);
retriever
        .getConfig()
        .onSuccess(entries -> {
            System.out.println(entries.encodePrettily());
        })
        .onFailure(ex -> {
            System.out.println(ex.getMessage());
        });
```