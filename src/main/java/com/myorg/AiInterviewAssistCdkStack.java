package com.myorg;

import software.amazon.awscdk.services.apigateway.IntegrationOptions;
import software.amazon.awscdk.services.apigateway.IntegrationResponse;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.Method;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.MethodResponse;
import software.amazon.awscdk.services.apigateway.MockIntegration;
import software.amazon.awscdk.services.apigateway.PassthroughBehavior;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AiInterviewAssistCdkStack extends Stack {
    public AiInterviewAssistCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AiInterviewAssistCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Create Lambda function
        Function aIInterviewAssistFunction = Function.Builder.create(this, "AiInterviewAssistLambda")
                .functionName("AiInterviewAssistLambda")
                .runtime(Runtime.JAVA_21)
                .code(Code.fromAsset("target/ai_interview_assist_cdk-0.1.jar")) // Point to your JAR file
                .handler("resources.AiInterviewAssistLambdaHandler::handleRequest") // Use the fully qualified class name
                .memorySize(512)
                .timeout(software.amazon.awscdk.Duration.seconds(300))
                .build();

        aIInterviewAssistFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "bedrock:InvokeModel",
                        "bedrock:ListFoundationModels"
                ))
                .resources(List.of("*"))
                .build());

        // Create API Gateway
        RestApi api = RestApi.Builder.create(this, "InterviewAssistApi")
                .restApiName("InterviewAssistApi")
                .build();

        // Create /interviewAssist resource
        Resource resource = api.getRoot().addResource("interviewAssist");

        // Add POST method with CORS support
        Method postMethod = resource.addMethod("POST", new LambdaIntegration(aIInterviewAssistFunction),
                MethodOptions.builder()
                        .methodResponses(List.of(
                                MethodResponse.builder()
                                        .statusCode("200")
                                        .responseParameters(
                                                Map.of(
                                                        "method.response.header.Access-Control-Allow-Origin", true,
                                                        "method.response.header.Access-Control-Allow-Headers", true,
                                                        "method.response.header.Access-Control-Allow-Methods", true
                                                )
                                        )
                                        .build()
                        ))
                        .build());

        // Add an OPTIONS method to properly handle preflight requests
        resource.addMethod("OPTIONS", new MockIntegration(IntegrationOptions.builder()
                .integrationResponses(List.of(
                        IntegrationResponse.builder()
                                .statusCode("200")
                                .responseParameters(
                                        Map.of(
                                                "method.response.header.Access-Control-Allow-Origin", "'*'",
                                                "method.response.header.Access-Control-Allow-Methods", "'OPTIONS,POST'",
                                                "method.response.header.Access-Control-Allow-Headers", "'Content-Type,Authorization'"
                                        )
                                )
                                .build()
                ))
                .passthroughBehavior(PassthroughBehavior.WHEN_NO_MATCH)
                .requestTemplates(Map.of("application/json", "{ \"statusCode\": 200 }"))
                .build()), MethodOptions.builder()
                .methodResponses(List.of(
                        MethodResponse.builder()
                                .statusCode("200")
                                .responseParameters(
                                        Map.of(
                                                "method.response.header.Access-Control-Allow-Origin", true,
                                                "method.response.header.Access-Control-Allow-Methods", true,
                                                "method.response.header.Access-Control-Allow-Headers", true
                                        )
                                )
                                .build()
                ))
                .build());
    }
}
