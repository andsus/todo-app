package dev.stratospheric.todoapp.cdk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.stratospheric.cdk.ApplicationEnvironment;
import dev.stratospheric.todoapp.cdk.stack.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.constructs.Construct;

import static dev.stratospheric.todoapp.cdk.Validations.requireNonEmpty;
import static java.util.Collections.singletonList;

public class ServiceApp {

  public static void main(final String[] args) {
    App app = new App();

    String environmentName = (String) app.getNode().tryGetContext("environmentName");
    requireNonEmpty(environmentName, "context variable 'environmentName' must not be null");

    String applicationName = (String) app.getNode().tryGetContext("applicationName");
    requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

    String accountId = (String) app.getNode().tryGetContext("accountId");
    requireNonEmpty(accountId, "context variable 'accountId' must not be null");

    String springProfile = (String) app.getNode().tryGetContext("springProfile");
    requireNonEmpty(springProfile, "context variable 'springProfile' must not be null");

    String dockerRepositoryName = (String) app.getNode().tryGetContext("dockerRepositoryName");
    requireNonEmpty(dockerRepositoryName, "context variable 'dockerRepositoryName' must not be null");

    String dockerImageTag = (String) app.getNode().tryGetContext("dockerImageTag");
    Validations.requireNonEmpty(dockerImageTag, "context variable 'dockerImageTag' must not be null");

    String region = (String) app.getNode().tryGetContext("region");
    requireNonEmpty(region, "context variable 'region' must not be null");

    Environment awsEnvironment = makeEnv(accountId, region);

    ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(
      applicationName,
      environmentName
    );

    long timestamp = System.currentTimeMillis();
    Stack parametersStack = new Stack(app, "ServiceParameters-" + timestamp, StackProps.builder()
      .stackName(applicationEnvironment.prefix("Service-Parameters-" + timestamp))
      .env(awsEnvironment)
      .build());

    Stack serviceStack = new Stack(app, "ServiceStack", StackProps.builder()
      .stackName(applicationEnvironment.prefix("Service"))
      .env(awsEnvironment)
      .build());

    CognitoStack.CognitoOutputParameters cognitoOutputParameters =
      CognitoStack.getOutputParametersFromParameterStore(parametersStack, applicationEnvironment);

    PostgresDatabase.DatabaseOutputParameters databaseOutputParameters =
      PostgresDatabase.getOutputParametersFromParameterStore(parametersStack, applicationEnvironment);

    MessagingStack.MessagingOutputParameters messagingOutputParameters =
      MessagingStack.getOutputParametersFromParameterStore(parametersStack, applicationEnvironment);

    ActiveMqStack.ActiveMqOutputParameters activeMqOutputParameters =
      ActiveMqStack.getOutputParametersFromParameterStore(parametersStack, applicationEnvironment);

    List<String> securityGroupIdsToGrantIngressFromEcs = Arrays.asList(
      databaseOutputParameters.getDatabaseSecurityGroupId(),
      activeMqOutputParameters.getActiveMqSecurityGroupId()
    );



    Service.DockerImageSource dockerImageSource = new Service.DockerImageSource(dockerRepositoryName, dockerImageTag);
    Network.NetworkOutputParameters networkOutputParameters = Network.getOutputParametersFromParameterStore(serviceStack, applicationEnvironment.getEnvironmentName());


    Service.ServiceInputParameters serviceInputParameters =
      new Service.ServiceInputParameters(dockerImageSource,
          securityGroupIdsToGrantIngressFromEcs,
          environmentVariables(
            serviceStack,
            databaseOutputParameters,
            cognitoOutputParameters,
            messagingOutputParameters,
            activeMqOutputParameters,
            springProfile,
            environmentName))
        .withStickySessionsEnabled(true)
        .withHealthCheckIntervalSeconds(30) // needs to be long enough to allow for slow start up with low-end computing instances
        .withHealthCheckPath("/actuator/health")
        .withAwsLogsDateTimeFormat("%Y-%m-%dT%H:%M:%S.%f%z")
        .withTaskRolePolicyStatements(List.of(
          PolicyStatement.Builder.create()
            .sid("AllowCreatingUsers")
            .effect(Effect.ALLOW)
            .resources(
              List.of(String.format("arn:aws:cognito-idp:%s:%s:userpool/%s", region, accountId, cognitoOutputParameters.getUserPoolId()))
            )
            .actions(List.of(
              "cognito-idp:AdminCreateUser"
            ))
            .build(),
          PolicyStatement.Builder.create()
            .sid("AllowSQSAccess")
            .effect(Effect.ALLOW)
            .resources(List.of(
              String.format("arn:aws:sqs:%s:%s:%s", region, accountId, messagingOutputParameters.getTodoSharingQueueName())
            ))
            .actions(Arrays.asList(
              "sqs:DeleteMessage",
              "sqs:GetQueueUrl",
              "sqs:ListDeadLetterSourceQueues",
              "sqs:ListQueues",
              "sqs:ListQueueTags",
              "sqs:ReceiveMessage",
              "sqs:SendMessage",
              "sqs:ChangeMessageVisibility",
              "sqs:GetQueueAttributes"))
            .build(),
          PolicyStatement.Builder.create()
            .sid("AllowSendingEmails")
            .effect(Effect.ALLOW)
            .resources(
              List.of(String.format("arn:aws:ses:%s:%s:identity/noreply@devops-prime-video-amazon-dev.awsapps.com", region, accountId))
            )
            .actions(List.of(
              "ses:SendEmail",
              "ses:SendRawEmail"
            ))
            .build(),
          PolicyStatement.Builder.create()
            .sid("AllowDynamoTableAccess")
            .effect(Effect.ALLOW)
            .resources(
              List.of(String.format("arn:aws:dynamodb:%s:%s:table/%s", region, accountId, applicationEnvironment.prefix("breadcrumbs")))
            )
            .actions(List.of(
              "dynamodb:Scan",
              "dynamodb:Query",
              "dynamodb:PutItem",
              "dynamodb:GetItem",
              "dynamodb:BatchWriteItem",
              "dynamodb:BatchWriteGet"
            ))
            .build(),
          PolicyStatement.Builder.create()
            .sid("AllowSendingMetricsToCloudWatch")
            .effect(Effect.ALLOW)
            .resources(singletonList("*")) // CloudWatch does not have any resource-level permissions, see https://stackoverflow.com/a/38055068/9085273
            .actions(singletonList("cloudwatch:PutMetricData"))
            .build()
          )
        );


    Service service = new Service(
      serviceStack,
      "Service",
      awsEnvironment,
      applicationEnvironment,
      serviceInputParameters,
      networkOutputParameters);

     app.synth();
  }

  static Map<String, String> environmentVariables(
    Construct scope,
    PostgresDatabase.DatabaseOutputParameters databaseOutputParameters,
    CognitoStack.CognitoOutputParameters cognitoOutputParameters,
    MessagingStack.MessagingOutputParameters messagingOutputParameters,
    ActiveMqStack.ActiveMqOutputParameters activeMqOutputParameters,
    String springProfile,
    String environmentName) {
    Map<String, String> vars = new HashMap<>();

    String databaseSecretArn = databaseOutputParameters.getDatabaseSecretArn();
    ISecret databaseSecret = Secret.fromSecretCompleteArn(scope, "databaseSecret", databaseSecretArn);

    vars.put("SPRING_PROFILES_ACTIVE", springProfile);
    vars.put("COGNITO_CLIENT_ID", cognitoOutputParameters.getUserPoolClientId());
    vars.put("COGNITO_CLIENT_SECRET", cognitoOutputParameters.getUserPoolClientSecret());
    vars.put("COGNITO_USER_POOL_ID", cognitoOutputParameters.getUserPoolId());
    vars.put("COGNITO_LOGOUT_URL", cognitoOutputParameters.getLogoutUrl());
    vars.put("COGNITO_PROVIDER_URL", cognitoOutputParameters.getProviderUrl());


    vars.put("SPRING_DATASOURCE_URL",
      String.format("jdbc:postgresql://%s:%s/%s",
        databaseOutputParameters.getEndpointAddress(),
        databaseOutputParameters.getEndpointPort(),
        databaseOutputParameters.getDbName()));
    vars.put("SPRING_DATASOURCE_USERNAME",
      databaseSecret.secretValueFromJson("username").toString());
    vars.put("SPRING_DATASOURCE_PASSWORD",
      databaseSecret.secretValueFromJson("password").toString());

    vars.put("TODO_SHARING_QUEUE_NAME", messagingOutputParameters.getTodoSharingQueueName());
    vars.put("WEB_SOCKET_RELAY_ENDPOINT", activeMqOutputParameters.getStompEndpoint());
    vars.put("WEB_SOCKET_RELAY_USERNAME", activeMqOutputParameters.getActiveMqUsername());
    vars.put("WEB_SOCKET_RELAY_PASSWORD", activeMqOutputParameters.getActiveMqPassword());
    vars.put("ENVIRONMENT_NAME", environmentName);
    return vars;
  }

  static Environment makeEnv(String account, String region) {
    return Environment.builder()
      .account(account)
      .region(region)
      .build();
  }
}