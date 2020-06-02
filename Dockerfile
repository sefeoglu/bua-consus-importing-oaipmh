FROM openjdk:11-jre

ENV VERTICLE_FILE piveau-importing-oaipmh.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

RUN addgroup --system vertx && adduser --system --group vertx

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

RUN chown -R vertx $VERTICLE_HOME
RUN chmod -R g+w $VERTICLE_HOME

USER vertx

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java $JAVA_OPTS -jar $VERTICLE_FILE"]
