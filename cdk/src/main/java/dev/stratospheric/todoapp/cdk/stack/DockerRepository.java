package dev.stratospheric.todoapp.cdk.stack;


import software.amazon.awscdk.Environment;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.constructs.Construct;

import java.util.Collections;
import java.util.Objects;

public class DockerRepository extends Construct {
  private final IRepository ecrRepository;
  public DockerRepository(
    final Construct scope,
    final String id,
    final Environment awsEnvironment,
    final DockerRepositoryInputParameters dockerRepositoryInputParameters) {
    super(scope, id);

    this.ecrRepository = Repository.Builder.create(this, "ecrReposiotory")
      .repositoryName(dockerRepositoryInputParameters.dockerRepositoryName)
      .removalPolicy(dockerRepositoryInputParameters.retainRegistryOnDelete ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
      .lifecycleRules(Collections.singletonList(LifecycleRule.builder()
        .rulePriority(1)
        .description("limit to " + dockerRepositoryInputParameters.maxImageCount + " images")
        .maxImageCount(dockerRepositoryInputParameters.maxImageCount)
        .build()))
      .build();

    // grant pull and push to all users of the account
    ecrRepository.grantPullPush(new AccountPrincipal(dockerRepositoryInputParameters.accountId));
  }

  public IRepository getEcrRepository() {
    return ecrRepository;
  }

  public static class DockerRepositoryInputParameters {
    private final String dockerRepositoryName;
    private final String accountId;
    private final int maxImageCount;
    private final boolean retainRegistryOnDelete;

    public DockerRepositoryInputParameters(String dockerRepositoryName, String accountId) {
      this.dockerRepositoryName = dockerRepositoryName;
      this.accountId = accountId;
      this.maxImageCount = 10;
      this.retainRegistryOnDelete = true;
    }

    public DockerRepositoryInputParameters(String dockerRepositoryName, String accountId, int maxImageCount) {
      Objects.requireNonNull(accountId, "accountId must not be null");
      Objects.requireNonNull(dockerRepositoryName, "dockerRepositoryName must not be null");
      this.dockerRepositoryName = dockerRepositoryName;
      this.accountId = accountId;
      this.maxImageCount = maxImageCount;
      this.retainRegistryOnDelete = true;
    }

    public DockerRepositoryInputParameters(String dockerRepositoryName, String accountId, int maxImageCount, boolean retainRegistryOnDelete) {
      Objects.requireNonNull(accountId, "accountId must not be null");
      Objects.requireNonNull(dockerRepositoryName, "dockerRepositoryName must not be null");
      this.dockerRepositoryName = dockerRepositoryName;
      this.accountId = accountId;
      this.maxImageCount = maxImageCount;
      this.retainRegistryOnDelete = retainRegistryOnDelete;
    }
  }
}
