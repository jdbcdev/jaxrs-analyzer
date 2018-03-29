#!/bin/bash
mvn deploy -DskipTests -DaltDeploymentRepository=nexus.wdf.sap.corp::default::http://nexussnap.wdf.sap.corp:8081/nexus/content/repositories/deploy.snapshots/
