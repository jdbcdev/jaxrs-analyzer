#!/bin/bash 
mvn clean deploy -DskipTests -DaltDeploymentRepository=local::default::file:/^/mvn-repo