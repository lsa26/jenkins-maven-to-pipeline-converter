# jenkins-maven-to-pipeline-converter
A Jenkins script to automatically convert Maven jobs to Jenkins Pipeline jobs, supporting Kubernetes agents detection and integration.

This script automates the conversion of Maven-based Jenkins jobs to Jenkins Pipeline jobs. It also detects Kubernetes-based agents and generates the appropriate pipeline scripts using Kubernetes Pod Templates.


## Prerequisites

![Animation GIF](https://github.com/lsa26/jenkins-maven-to-pipeline-converter/blob/main/gif/mavenjob-to-pipeline.gif?raw=true)



Before using this script, make sure you have the following:

- Jenkins Installed: Ensure you have Jenkins running with administrative access.

- Jenkins Kubernetes Plugin: The script uses the Kubernetes plugin for Jenkins. Make sure it's installed and configured properly.

- Maven Jobs: The script is designed to convert existing Maven jobs into Jenkins Pipeline jobs. You need Maven-based Jenkins jobs.

- Jenkins Folder: The script targets a specific folder containing Maven jobs for conversion. Ensure you know the folder name.

- Jenkins Script Console Access: The script must be run from the Jenkins Script Console to have access to the Jenkins.instance object and related Jenkins APIs.


## Setup and Usage

### Step 1: Install the Necessary Jenkins Plugins

Ensure that the following plugins are installed in your Jenkins instance:

- Kubernetes Plugin: For Kubernetes integration and management.

- Pipeline Plugin: For handling Jenkins Pipelines.

- Maven Plugin: To support Maven jobs (should be pre-installed in most Jenkins instances).

### Step 2: Prepare the Script

- Download or copy the following script into the Jenkins Script Console:

```
import jenkins.model.*
import com.cloudbees.hudson.plugins.folder.Folder
import org.csanchez.jenkins.plugins.kubernetes.*

// Folder where Maven jobs are located
def folderName = "foldername"  // 🔹 Replace with the actual folder name

// Retrieve the folder from Jenkins
def currentFolder = Jenkins.instance.getItemByFullName(folderName, Folder)

if (currentFolder == null) {
    println "❌ Folder '${folderName}' does not exist!"
    return
} else {
    println "📂 Folder '${folderName}' found!"
}

// Get all Kubernetes clouds from the controller
def kubernetesClouds = Jenkins.instance.clouds.findAll { it instanceof KubernetesCloud }

if (kubernetesClouds.isEmpty()) {
    println "❌ No Kubernetes cloud found!"
    return
} else {
    println "☁️ Found ${kubernetesClouds.size()} Kubernetes cloud(s):"
    kubernetesClouds.each { cloud ->
        println "Cloud Kubernetes: ${cloud.name}"
        
        // List the Pod Templates associated with this Kubernetes cloud
        cloud.getTemplates().each { template ->
            println "  - Pod Template: ${template.name} with label: ${template.label}"
        }
    }
}

// Get all jobs in the folder
def selectedJobs = currentFolder.getItems()

// Iterate through the jobs in the folder
selectedJobs.each { job ->
    if (job instanceof hudson.maven.MavenModuleSet) {
        println "🔄 Converting job: ${job.name}"

        try {
            // Retrieve SCM information
            def scm = job.scm
            def gitUrl = scm?.getUserRemoteConfigs()?.get(0)?.getUrl() ?: ''
            def gitBranch = scm?.getBranches()?.get(0)?.getName()?.replaceAll('^\\*/', '') ?: 'main'

            println "🔍 Git repository: ${gitUrl}"
            println "🔍 Branch: ${gitBranch}"

            // Retrieve agent label
            def agentLabel = job.getAssignedLabelString() ?: 'default-agent'
            println "🖥 Using agent: ${agentLabel}"

            // Determine if it's a Kubernetes agent by checking Pod Templates in the Kubernetes clouds
            def isKubernetesAgent = false
            kubernetesClouds.each { cloud ->
                cloud.getTemplates().each { template ->
                    if (template.label == agentLabel) {
                        isKubernetesAgent = true
                        println "⚙️ Kubernetes agent detected: ${agentLabel} (from cloud ${cloud.name})"
                    }
                }
            }

            if (!isKubernetesAgent) {
                println "⚙️ Standard agent detected: ${agentLabel}"
            }

            // Retrieve credentials if required
            def credentialsId = scm?.getUserRemoteConfigs()?.get(0)?.getCredentialsId() ?: ''
            if (credentialsId) {
                println "🔑 Using credentials ID: ${credentialsId}"
            } else {
                println "⚠️ No credentials found. Ensure the repository is publicly accessible or configure credentials."
            }

            // Generate the pipeline script based on the agent type
            def pipelineScript
            if (isKubernetesAgent) {
                // Kubernetes Agent - use the label as is and container() syntax
                pipelineScript = """
                pipeline {
                    agent {
                        label '${agentLabel}'
                    }
                    stages {
                        stage('Checkout') {
                            steps {
                                script {
                                    sh 'git --version'
                                    if ('${credentialsId}') {
                                        git url: '${gitUrl}', branch: '${gitBranch}', credentialsId: '${credentialsId}'
                                    } else {
                                        git url: '${gitUrl}', branch: '${gitBranch}'
                                    }
                                }
                            }
                        }
                        stage('Build') {
                            steps {
                                container('${agentLabel}') {
                                    sh 'mvn clean install -DskipTests'
                                }
                            }
                        }
                    }
                }
                """
            } else {
                // Standard Agent - no container() syntax
                pipelineScript = """
                pipeline {
                    agent { label '${agentLabel}' }
                    stages {
                        stage('Checkout') {
                            steps {
                                script {
                                    sh 'git --version'
                                    if ('${credentialsId}') {
                                        git url: '${gitUrl}', branch: '${gitBranch}', credentialsId: '${credentialsId}'
                                    } else {
                                        git url: '${gitUrl}', branch: '${gitBranch}'
                                    }
                                }
                            }
                        }
                        stage('Build') {
                            steps {
                                sh 'mvn clean install -DskipTests'
                            }
                        }
                    }
                }
                """
            }

            // Create a new Pipeline job inside the same folder
            def pipelineJob = currentFolder.createProject(org.jenkinsci.plugins.workflow.job.WorkflowJob, job.name + "-pipeline")
            pipelineJob.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(pipelineScript, true))
            pipelineJob.save()

            println "✅ Job '${job.name}' successfully converted to a Pipeline!"
        } catch (Exception e) {
            println "❌ Error while converting job '${job.name}': ${e.message}"
            e.printStackTrace()
        }
    }
}

println "🎉 Conversion completed!"

```

### Step 3: Run the Script in Jenkins

Go to Jenkins and log in with administrative rights.

Navigate to Manage Jenkins → Script Console.

Copy and paste the entire script into the console.

Modify the folderName variable in the script to match the folder where your Maven jobs are located.

Click on Run to execute the script.

The script will:

- Detect all Kubernetes clouds in Jenkins.

- List Pod Templates from each Kubernetes cloud.

- Iterate over Maven jobs in the specified folder.

- Convert each job to a Jenkins Pipeline, adjusting the agent configuration based on Kubernetes templates or standard agents.

- Create new pipeline jobs for each Maven job.

### Step 4: Verify the Pipeline Jobs

After running the script, new pipeline jobs will be created in the same folder with the name <original-job-name>-pipeline.

You can verify the newly created pipelines by:

- Navigating to the folder in Jenkins.

- Checking that each Maven job has a corresponding pipeline job.

### Troubleshooting
- No Kubernetes Cloud Found: Ensure that the Kubernetes plugin is properly installed and configured in Jenkins. You should have at least one Kubernetes cloud configured under Manage Jenkins → Configure System → Clouds.

- Job Conversion Fails: If a job fails to convert, check the console output for specific error messages. Make sure the job is a valid Maven job and has a properly configured Git SCM.

### Contribution
Feel free to contribute by submitting pull requests or opening issues for any bugs or improvements.

### License
This script is released under the MIT License. See the LICENSE file for more information.
