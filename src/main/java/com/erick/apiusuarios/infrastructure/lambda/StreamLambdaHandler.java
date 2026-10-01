package com.erick.apiusuarios.infrastructure.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.erick.apiusuarios.ApiusuariosApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** API Gateway REST API, Lambda proxy integration (payload version 1.0). */
public class StreamLambdaHandler implements RequestStreamHandler {
    private final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    private static class Container {
        private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> INSTANCE =
                initialize(ApiusuariosApplication.class, "lambda");
    }

    public StreamLambdaHandler() {
        this.handler = Container.INSTANCE;
    }

    // Allows integration tests to supply isolated database/storage configuration.
    StreamLambdaHandler(Class<?> application, String... profiles) {
        this.handler = initialize(application, profiles);
    }

    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> initialize(
            Class<?> application, String... profiles) {
        try {
            var container = SpringBootLambdaContainerHandler.getAwsProxyHandler(application, profiles);
            // Base64 preserves arbitrary file bytes, including text files with unknown encoding.
            container.getContainerConfig().addBinaryContentTypes("*/*");
            return container;
        } catch (ContainerInitializationException e) {
            throw new IllegalStateException("No se pudo inicializar Spring para AWS Lambda", e);
        }
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        handler.proxyStream(input, output, context);
    }
}
