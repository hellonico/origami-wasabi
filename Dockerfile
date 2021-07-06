FROM openjdk:8-jdk
EXPOSE 8080:8080
RUN mkdir /app
COPY build/install/wasabi/ /app/
RUN mkdir /app/bin/out
WORKDIR /app/bin
CMD ["./wasabi"]