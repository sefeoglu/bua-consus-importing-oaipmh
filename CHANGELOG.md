# ChangeLog

## Unreleased

## 1.3.5 (2023-14-30)

**Fixed:**
* Evaluate `preProcessing` from config for pre-processing 

**Changed:**
* Lib updates

## 1.3.4 (2022-04-20)

**Changed:**
* Lib updates

## 1.3.3 (2021-11-21)

**Fixed:**
* Close streams explicitly

**Changed:**
* Catch exception from pre-processing

## 1.3.2 (2021-06-05)

**Changed:**
* Lib dependencies

## 1.3.1 (2021-02-12)

**Fixed:**
* Empty dataset record check

## 1.3.0 (2021-01-30)

**Added:**
* Generic record forwarding

**Changed:**
* Switch to Vert.x 4.0.0

## 1.2.0 (2020-07-30)

**Added:**
* Configurable default oaipmh adapter uri

**Changed:**
* Configuration in segment config of pipe
 
## 1.1.1 (2020-06-18)

**Changed:**
* Serialize pipe startTime as ISO standard string
 
## 1.1.0 (2020-06-02)

**Added:**
* `queries` pipe configuration

**Changed:**
* Build and packaging tool from shade plugin to vertx plugin
 
## 1.0.2 (2020-02-28)

**Changed:**
* Connector lib update

## 1.0.1 (2019-12-14)

**Added:**
* Pre-processing to fix malformed URIRefs

## 1.0.0 (2019-11-08)

**Added:**
* buildInfo.json for build info via `/health` path
* config.schema.json
* Configuration change listener
* `PIVEAU_LOG_LEVEL` for general `io.piveau` package log level configuration
* `sendHash` for configuring optional hash calculation in pipe
* `sendHash` to config schema
 
**Changed:**
* `PIVEAU_` prefix to logstash configuration environment variables
* Hash is optional and calculation is based on canonical algorithm
* Requires now latest LTS Java 11
* Docker base image to openjdk:11-jre

**Fixed:**
* Update all dependencies

## 0.1.0 (2019-05-17)

**Added:**
* `catalogue` read from configuration and pass it to the info object
* Environment `PIVEAU_IMPORTING_SEND_LIST_DELAY` for a configurable delay
* `sendListDelay` pipe configuration option

**Changed:**
* Readme

**Removed:**
* `mode` configuration and fetchIdentifier

## 0.0.1 (2019-05-03)

Initial release
