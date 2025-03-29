package resources;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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


    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log("Starting Lambda: " + input.toString());
        Map<String, Object> response = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        // CORS headers
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS, POST");
        headers.put("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Amz-Date, X-Api-Key, X-Amz-Security-Token, Accept, Origin, Cache-Control, X-Requested-With");

        try {
            // Extract prompt from input
            Object promptBody = input.get("body");
            String prompt = promptBody != null ? objectMapper.readTree(promptBody.toString()).path("prompt").asText() : null;
            if (prompt == null || prompt.trim().isEmpty()) {
                logger.log("Prompt cannot be empty");
                throw new IllegalArgumentException("Prompt cannot be empty");
            }

            // Invoke Nova and get response
            String novaResponse;
            try {
                // Build the request body as JSON
                ObjectNode requestBody = objectMapper.createObjectNode();

                // Add inferenceConfig
                requestBody.putObject("inferenceConfig")
                        .put("maxTokens", 1000)
                        .put("temperature", 0.5F); // Adjusts randomness

                // Add system prompt
                requestBody.putArray("system").addObject().put("text","Welcome! I am your AI Interview Helper, here to assist you in preparing for job interviews. Simply provide your job role and a brief description of your responsibilities or required skills, and I'll generate a **personalized interview preparation guide** to help you succeed.\\n\\n### What You'll Get:\\n- **Study Plan:** A structured timeline with essential topics to review and practice sessions.\\n- **Mock Interview Questions:** Realistic technical and behavioral questions tailored to your job role.\\n- **Answer Strategies:** Effective frameworks, sample answers, and key points to improve your responses.\\n- **Skill Assessment:** Self-evaluation quizzes to track your progress and focus on improvement areas.\\n\\nIf your input is unclear or incomplete, I’ll ask for clarification and provide examples of well-structured job roles and descriptions to guide you.\\n\\n### Example Inputs & Expected Responses:\\n\\n1\uFE0F⃣ **Input:** \\\"Software Engineer at a FinTech company, responsible for backend development with Java and AWS.\\\"\\n   **Response:** \\\"Here’s your interview preparation guide:\\n   - **Study Plan:**\\n     - Week 1: Java Core Concepts  \\n     - Week 2: AWS & Cloud Fundamentals  \\n     - Week 3: System Design Principles  \\n   - **Mock Questions:**\\n     - How does Java handle memory management?\\n     - Explain the CAP theorem in the context of distributed databases.\\n   - **Answer Strategies:**\\n     - Use the STAR framework for behavioral questions.\\n     - Discuss trade-offs when choosing between SQL and NoSQL databases.\\\"\\n\\n2\uFE0F⃣ **Input:** \\\"Marketing role, needs social media experience.\\\"\\n   **Response:** \\\"Here’s your tailored study guide:\\n   - **Study Plan:**\\n     - Week 1: Social Media Analytics  \\n     - Week 2: Content Strategy Development  \\n     - Week 3: Paid Ad Campaigns and ROI Analysis  \\n   - **Mock Questions:**\\n     - How do you measure the success of a social media campaign?\\n     - What tools do you use for social media analytics?\\n   - **Answer Strategies:**\\n     - Provide data-driven examples and campaign performance metrics.\\\"\\n\\n3\uFE0F⃣ **Input:** \\\"How do I negotiate my salary?\\\"\\n   **Response:** \\\"Please provide a job role and industry to receive a more relevant guide. For example:\\n   - **Software Engineer:** Backend development in FinTech using Java and AWS.\\n   - **Data Analyst:** SQL & Python for business insights.\\n   - **Product Manager:** Leading roadmap development for SaaS products.\\\"\\n\\nLet's get started—please share your job role and description!\"\n");

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
                InvokeModelResponse invokeModelResponse = bedrockClient.invokeModel(InvokeModelRequest.builder()
                        .modelId(NOVA_MODEL_ID)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromString(requestBodyString, StandardCharsets.UTF_8))
                        .build());

                logger.log("Response from Nova: " + invokeModelResponse.body().asUtf8String());
                // Parse and return response text
                novaResponse = objectMapper.readTree(invokeModelResponse.body().asUtf8String())
                        .path("output").path("message").path("content").path(0).path("text").asText();

                logger.log("Parsed response: " + novaResponse);

            } catch (Exception e) {
                logger.log("Error invoking Amazon Nova: " + e.getMessage());
                throw new RuntimeException("Error invoking Amazon Nova: " + e.getMessage(), e);
            }

            // Prepare success response
            response.put("statusCode", 200);
            response.put("headers", headers);
            response.put("body", objectMapper.writeValueAsString(
                    Map.of("response", novaResponse)
            ));

        } catch (Exception e) {
            logger.log("Error processing request: " + e);
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

        logger.log("Ending Lambda: " + response);
        return response;
    }
}
