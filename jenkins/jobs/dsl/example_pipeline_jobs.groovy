// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
// **The git repo variables will be changed to the users' git repositories manually in the Jenkins jobs**
def partsUnlimitedAppgitRepo = "PartsUnlimited"
def partsUnlimitedAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + partsUnlimitedAppgitRepo
//def regressionTestGitRepo = "YOUR_REGRESSION_TEST_REPO"
//def regressionTestGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + regressionTestGitRepo

// Jobs
def buildAppJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Build")
def unitTestJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Unit_Tests")
def codeAnalysisJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Code_Analysis")
def deployJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Deploy")
def regressionTestJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Regression_Tests")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/partsUnlimited_Application")

pipelineView.with{
    title('partsUnlimited Application Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/Parts_Unlimited_Build")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

// All jobs are tied to build on the Jenkins slave
// The functional build steps for each job have been left empty
// A default set of wrappers have been used for each job
// New jobs can be introduced into the pipeline as required

buildAppJob.with{
	description("partsUnlimited application build job.")
	scm{
		git{
			remote{
				url(partsUnlimitedAppGitUrl)
				credentials("adop-jenkins-master")
			}
			branch("*/master")
		}
	}
	environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
    }
	label("docker")
	wrappers {
		preBuildCleanup()
		injectPasswords()
		maskPasswords()
		sshAgent("adop-jenkins-master")
	}
	triggers{
		gerrit{
		  events{
			refUpdated()
		  }
		  configure { gerritxml ->
			gerritxml / 'gerritProjects' {
			  'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject' {
				compareType("PLAIN")
				pattern(projectFolderName + "/" + partsUnlimitedAppgitRepo)
				'branches' {
				  'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch' {
					compareType("PLAIN")
					pattern("master")
				  }
				}
			  }
			}
			gerritxml / serverName("ADOP Gerrit")
		  }
		}
	}
	steps {
		shell('''
            |
            |set -x
            |
            |echo "Mount the source code into a container that will build the dotnet binary"
            |
            |docker run -t --rm -v jenkins_slave_home:/build \\
            |            ifourmanov/adop-asp-build \\
            |            bash -c "source /root/.dnx/dnvm/dnvm.sh && \\
            |    		cd /build/${JOB_NAME}/src/PartsUnlimited.Models/ && \\
            |    		dnu restore && \\
            |    		cd /build/${JOB_NAME}/src/PartsUnlimitedWebsite && \\
            |    		dnu restore && \\
            |    		dnu publish && \\
            |    		echo done"
            |
            |set +x
            |'''.stripMargin())
	}
	publishers{
		downstreamParameterized{
		  trigger(projectFolderName + "/Parts_Unlimited_Unit_Tests"){
			condition("UNSTABLE_OR_BETTER")
			parameters{
			  predefinedProp("B",'${BUILD_NUMBER}')
			  predefinedProp("PARENT_BUILD", '${JOB_NAME}')
			}
		  }
		}
	}
}

unitTestJob.with{
  description("This job runs unit tests on our partsUnlimited application.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Parts_Unlimited_Build","Parent build name")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
  }
  steps {
    shell('''
            |set -x
            |
            |echo "Mount the source code into a container that will run the unit tests"
            |
            |docker run --rm -v jenkins_slave_home:/jenkins_slave_home/ \\
            |            		ifourmanov/adop-asp-build \\
            |			bash -c "source /root/.dnx/dnvm/dnvm.sh && \\
            |     			cd /jenkins_slave_home/$JOB_NAME/test/PartsUnlimited.UnitTests/ && \\
            |     			dnu restore && \\
            |     			dnx test"
            |
            |set +x
            |'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Parts_Unlimited_Code_Analysis"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD",'${PARENT_BUILD}')
        }
      }
    }
  }
}

codeAnalysisJob.with{
  description("This job runs code quality analysis for our partsUnlimited application using SonarQube.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Parts_Unlimited_Build","Parent build name")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("docker")
  steps {
    copyArtifacts('Reference_Application_Build') {
        buildSelector {
          buildNumber('${B}')
      }
    }
  }
  configure { myProject ->
    myProject / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'(plugin:"sonar@2.2.1"){
      properties('''sonar.projectKey=org.java.reference-application
sonar.projectName=Reference application
sonar.projectVersion=1.0.0
sonar.sources=src
sonar.language=java
sonar.sourceEncoding=UTF-8
sonar.scm.enabled=false''')
      javaOpts()
      jdk('(Inherit From Job)')
      task()
    }
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Parts_Unlimited_Deploy"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

deployJob.with{
  description("This job deploys the partsUnlimited application to the CI environment")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Parts_Unlimited_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
    shell('''
            |
            |echo "Deploying"
            |cd ${WORKSPACE}/src/PartsUnlimitedWebsite/bin/
            |
            |cat <<EOF > Dockerfile
            |FROM microsoft/aspnet
            |COPY output /app
            |WORKDIR /app
            |EXPOSE 5001
            |ENTRYPOINT ["approot/Kestrel"]
            |EOF
            |
            |
            |docker kill asplinux || true
            |docker rm asplinux || true
            |docker build -t refapp:${BUILD_NUMBER} .
            |docker run -d -p 5001:5001 --name asplinux refapp:${BUILD_NUMBER}
            |
            |'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Parts_Unlimited_Regression_Tests"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
          predefinedProp("ENVIRONMENT_NAME", '${ENVIRONMENT_NAME}')
        }
      }
    }
  }
}

regressionTestJob.with{
  description("This job runs regression tests on the deployed partsUnlimited application")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Parts_Unlimited_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }

  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
    shell('''## YOUR REGRESSION TESTING STEPS GO HERE'''.stripMargin())
  }
}
