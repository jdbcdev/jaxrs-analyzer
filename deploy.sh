#!/bin/bash 
mvn clean deploy -DskipTests -DaltDeploymentRepository=nexus.wdf.sap.corp::default::http://nexus.wdf.sap.corp:8081/nexus/content/repositories/deploy.snapshots/
