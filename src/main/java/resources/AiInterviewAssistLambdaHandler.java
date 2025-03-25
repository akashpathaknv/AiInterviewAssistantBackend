package resources;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AiInterviewAssistLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    private static final String NOVA_MODEL_ID = "amazon.nova-micro-v1:0";

    public AiInterviewAssistLambdaHandler() {
        this.bedrockClient = BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private String invokeNova(String prompt) {
        try {
            // Build the request body as JSON
            ObjectNode requestBody = objectMapper.createObjectNode();

            // Add inferenceConfig
            requestBody.putObject("inferenceConfig")
                    .put("maxTokens", 500)
                    .put("temperature", 0.5F); // Adjusts randomness

            // Add messages array
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");

            // Add content array to the message
            ArrayNode content = message.putArray("content");
            content.addObject().put("text", prompt);

            // Convert request body to JSON string
            String requestBodyString = objectMapper.writeValueAsString(requestBody);

            // Invoke the model
            InvokeModelResponse response = bedrockClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(NOVA_MODEL_ID)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromString(requestBodyString, StandardCharsets.UTF_8))
                    .build());

            // Parse and return response text
            return objectMapper.readTree(response.body().asUtf8String())
                    .path("messages").path(0).path("content").path(0).path("text").asText();

        } catch (Exception e) {
            throw new RuntimeException("Error invoking Amazon Nova: " + e.getMessage(), e);
        }
    }


    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        // CORS headers
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS, POST");
        headers.put("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Amz-Date, X-Api-Key, X-Amz-Security-Token, Accept, Origin, Cache-Control, X-Requested-With");

        try {
            // Extract prompt from input
            String prompt = (String) input.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                throw new IllegalArgumentException("Prompt cannot be empty");
            }

            // Invoke Nova and get response
            String novaResponse = invokeNova(prompt);

            // Prepare success response
            response.put("statusCode", 200);
            response.put("headers", headers);
            response.put("body", objectMapper.writeValueAsString(
                    Map.of("response", novaResponse)
            ));

        } catch (Exception e) {
            // Handle errors
            response.put("statusCode", 500);
            response.put("headers", headers);
            try {
                response.put("body", objectMapper.writeValueAsString(
                        Map.of("error", e.getMessage())
                ));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        return response;
    }
}
