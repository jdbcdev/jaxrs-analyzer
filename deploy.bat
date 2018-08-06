set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_181
mvn deploy -DskipTests -DaltDeploymentRepository=nexus.wdf.sap.corp::default::http://nexussnap.wdf.sap.corp:8081/nexus/content/repositories/deploy.snapshots/
