package dev.stratospheric.todoapp.cdk;

import dev.stratospheric.todoapp.cdk.stack.ApplicationEnvironment;
import dev.stratospheric.todoapp.cdk.stack.BreadcrumbsDynamoDbTable;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

public class DynamoDbApp {
  public static void main(final String[] args) {
    App app = new App();

    String environmentName = (String) app.getNode().tryGetContext("environmentName");
    Validations.requireNonEmpty(environmentName, "context variable 'environmentName' must not be null");

    String applicationName = (String) app.getNode().tryGetContext("applicationName");
    Validations.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

    String accountId = (String) app.getNode().tryGetContext("accountId");
    Validations.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

    String region = (String) app.getNode().tryGetContext("region");
    Validations.requireNonEmpty(region, "context variable 'region' must not be null");

    Environment awsEnvironment = makeEnv(accountId, region);

    ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(
      applicationName,
      environmentName
    );

    Stack dynamoDbStack = new Stack(app, "DynamoDbStack", StackProps.builder()
      .stackName(applicationEnvironment.prefix("DynamoDb"))
      .env(awsEnvironment)
      .build());

    new BreadcrumbsDynamoDbTable(
      dynamoDbStack,
      "BreadcrumbTable",
      applicationEnvironment,
      new BreadcrumbsDynamoDbTable.InputParameter("breadcrumbs")
    );

    app.synth();
  }

  static Environment makeEnv(String account, String region) {
    return Environment.builder()
      .account(account)
      .region(region)
      .build();
  }

}
