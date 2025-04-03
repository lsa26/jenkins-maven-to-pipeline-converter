# jenkins-maven-to-pipeline-converter
A Jenkins script to automatically convert Maven jobs to Jenkins Pipeline jobs, supporting Kubernetes agents detection and integration.

This script automates the conversion of Maven-based Jenkins jobs to Jenkins Pipeline jobs. It also detects Kubernetes-based agents and generates the appropriate pipeline scripts using Kubernetes Pod Templates.


## Prerequisites

Before using this script, make sure you have the following:

Jenkins Installed: Ensure you have Jenkins running with administrative access.

Jenkins Kubernetes Plugin: The script uses the Kubernetes plugin for Jenkins. Make sure it's installed and configured properly.

Maven Jobs: The script is designed to convert existing Maven jobs into Jenkins Pipeline jobs. You need Maven-based Jenkins jobs.

Jenkins Folder: The script targets a specific folder containing Maven jobs for conversion. Ensure you know the folder name.

Jenkins Script Console Access: The script must be run from the Jenkins Script Console to have access to the Jenkins.instance object and related Jenkins APIs.
