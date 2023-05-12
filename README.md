# pivea-consus-importing-oaipmh-dc
Importing records using oai_dc metaprefix via the OAI-PMH protocol and feeding a pipe. 
The repository is based on piveau importing oai-pmh library, which imports records via the OAI-PMH protocol and feeding a pipe.

The service is based on the piveau-pipe-connector library. Any configuration applicable for the piveau-pipe-connector can also be used for this service.

## Table of Contents
1. [Build](#build)
1. [Run](#run)
1. [Docker](#docker)
1. [Configuration](#configuration)
    1. [Pipe](#pipe)
    1. [Data Info Object](#data-info-object)
    1. [Environment](#environment)
    1. [Logging](#logging)
1. [License](#license)

## Build
Requirements:
 * Git
 * Maven 3
 * Java 11

```bash
$ git clone <gitrepouri>
$ cd piveau-consus-importing-oaipmh
$ mvn package
```

## Run

```bash
$ java -jar target/importing-oaipmh.jar
```

## Docker

Build docker image:

```bash
$ docker build -t piveau/piveau-importing-oaipmh .
```

Run docker image:

```bash
$ docker run -it -p 8080:8080 piveau/piveau-importing-oaipmh
```
## Configuration

### Pipe

_mandatory_

* `address` 

    Address of the source

* `catalogue`

    The id of the target catalogue

_optional_

* `queries`

    Map of query parameters to use in request.
    
* `outputFormat` 
    
    Mimetype to use for payload. Default is `application/n-triples`

    Possible output formats are:

     * `application/rdf+xml`
     * `application/n-triples`
     * `application/ld+json`
     * `application/trig`
     * `text/turtle`
     * `text/n3`

* `sendListDelay`

    The delay in milliseconds before the list of identifiers is send. Take precedence over service configuration (see `PVEAU_IMPORTING_SEND_LIST_DELAY`)

* `sendHash`

    Append a hash value to the dataInfo object. Default is `false` 

### Data Info Object

* `total` 

    Total number of datasets

* `counter` 

    The number of this dataset

* `identifier` 

    The unique identifier in the source of this dataset

* `catalogue`

    The id of the target catalogue

* `hash` 

    The hash value calculated at the source

### Environment
See also piveau-pipe-connector

| Variable| Description | Default Value |
| :--- | :--- | :--- |
| `PIVEAU_IMPORTING_SEND_LIST_DELAY` | The delay in millisecond for sending the identifier list after the last dataset | `8000` |
| `PIVEAU_OAIPMH_ADAPTER_URI` | The address of the oai-pmh adapter | - |

### Logging
See [logback](https://logback.qos.ch/documentation.html) documentation for more details

| Variable| Description | Default Value |
| :--- | :--- | :--- |
| `PIVEAU_PIPE_LOG_APPENDER` | Configures the log appender for the pipe context | `STDOUT` |
| `PIVEAU_LOGSTASH_HOST`            | The host of the logstash service | `logstash` |
| `PIVEAU_LOGSTASH_PORT`            | The port the logstash service is running | `5044` |
| `PIVEAU_PIPE_LOG_PATH`     | Path to the file for the file appender | `logs/piveau-pipe.%d{yyyy-MM-dd}.log` |
| `PIVEAU_PIPE_LOG_LEVEL`    | The log level for the pipe context | `INFO` |
| `PIVEAU_LOG_LEVEL`    | The general log level for the `io.piveau` package | `INFO` |

## License

[Apache License, Version 2.0](LICENSE.md)
