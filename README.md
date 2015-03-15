Usage
=====

Add maven dependency (not on Maven Central yet!):

```
<dependency>
	<groupId>com.github.neothemachine</groupId>
	<artifactId>gwt-webdriver-junit-runstyle</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<scope>test</scope>
</dependency>
```

Adjust the runstyle used by gwt-maven-plugin:

		<build>
			<plugins>
				...
				<plugin>
					<groupId>org.codehaus.mojo</groupId>
					<artifactId>gwt-maven-plugin</artifactId>
					<version>2.5.1</version>
					<executions>
						<execution>
							<goals>
								<goal>test</goal>
							</goals>
						</execution>
					</executions>
					<configuration>
						<productionMode>true</productionMode>
						<mode>com.github.neothemachine.gwt.junit.RunStyleWebDriver:localhost:4444/*firefox</mode>
					</configuration>
				</plugin>
			</plugins>
		</build>
		
If your remote has the GWT plugin and you want to use dev mode for testing, then leave out `<productionMode>true</productionMode>`.

gwt-maven-plugin < 2.5.1
------------------------

If you use gwt-maven-plugin < 2.5.1 then the runstyle cannot be defined as above. Instead, use the following work-around:

		<build>
			<plugins>
				...
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
			</plugins>
		</build>
