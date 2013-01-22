Usage
=====

Add maven dependency:

```
<dependency>
	<groupId>com.github.neothemachine</groupId>
	<artifactId>gwt-webdriver-junit-runstyle</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<scope>test</scope>
</dependency>
```

Add surefire config:

```
  <plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.13</version>
    <configuration>
      <additionalClasspathElements>
        <additionalClasspathElement>${project.build.sourceDirectory}</additionalClasspathElement>
        <additionalClasspathElement>${project.build.testSourceDirectory}</additionalClasspathElement>
      </additionalClasspathElements>
      <useManifestOnlyJar>false</useManifestOnlyJar>
      <forkMode>always</forkMode>
      <systemProperties>
        <property>
          <name>gwt.args</name>
          <value>-prod -runStyle com.github.neothemachine.gwt.junit.RunStyleWebDriver:localhost:4444/*firefox -out ${project.build.directory}/${project.build.finalName}</value>
        </property>
      </systemProperties>
    </configuration>
  </plugin>
```

Known issues
============

At the moment, the gwt-maven-plugin cannot be used, as it is 
[hardcoded](https://github.com/gwt-maven-plugin/gwt-maven-plugin/blob/master/src/main/java/org/codehaus/mojo/gwt/shell/TestMojo.java#L298)
for the built-in JUnit run styles supported in GWT. 