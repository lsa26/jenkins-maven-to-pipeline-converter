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
