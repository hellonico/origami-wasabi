FROM eclipse-temurin:17-jdk
EXPOSE 8095
RUN mkdir /app
COPY build/install/wasabi/ /app/
RUN mkdir /app/bin/out
WORKDIR /app/bin
CMD ["./wasabi"]