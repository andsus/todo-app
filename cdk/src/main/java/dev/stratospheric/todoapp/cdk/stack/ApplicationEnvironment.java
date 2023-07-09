package dev.stratospheric.todoapp.cdk.stack;

import software.amazon.awscdk.Tags;
import software.constructs.IConstruct;

public class ApplicationEnvironment {
    private final String applicationName;
    private final String environmentName;

    public ApplicationEnvironment(String applicationName, String environmentName) {
        this.applicationName = applicationName;
        this.environmentName = environmentName;
    }

    public String getApplicationName() {
        return applicationName;
    }


    public String getEnvironmentName() {
        return environmentName;
    }

  /***
   * Strips non-alphanumeric characters from String, some AWS resources dislike
   * them when using them in resource names
   */
  private String sanitize(String environmentName) {
      return environmentName.replaceAll("[^a-zA-Z0-9-]", "");
  }

  @Override
  public String toString() {
    return sanitize(environmentName + "-" + applicationName);
  }
  // Prefixes with the application name and environment name
  public String prefix(String string) {
    return this + "-" + string;
  }
  /***
   * Prefixes a string with the application name and environment name. Returns only the last <code>characterLimit</code>
   *   characters from the name.
   */
  public String prefix(String string, int characterLimit) {
    String name = this.prefix(string);
    if (name.length() <= characterLimit) {
      return name;
    }
    return name.substring(name.length() - 21);
  }

  public void tag(IConstruct construct) {
    Tags.of(construct).add("application", applicationName);
    Tags.of(construct).add("environment", environmentName);
  }

}
