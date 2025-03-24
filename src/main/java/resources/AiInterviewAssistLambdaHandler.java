package resources;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.HashMap;
import java.util.Map;

public class AiInterviewAssistLambdaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        // Proper CORS headers
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS, POST");
        headers.put("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Amz-Date, X-Api-Key, X-Amz-Security-Token, Accept, Origin, Cache-Control, X-Requested-With");

        try {
            // Check if the request is a preflight (OPTIONS request)
            if ("OPTIONS".equalsIgnoreCase((String) input.get("httpMethod"))) {
                response.put("statusCode", 200);
                response.put("headers", headers);
                response.put("body", "");
                return response; // Exit early for preflight requests
            }

            // Regular request handling
            response.put("statusCode", 200);
            response.put("headers", headers);
            response.put("body", "Thanks for trying AI Interview Assist, Feature is still in progress!!");

        } catch (Exception e) {
            response.put("statusCode", 500);
            response.put("headers", headers); // Ensure headers are included in error response
            response.put("body", "Error: " + e.getMessage());
        }

        return response;
    }
}
