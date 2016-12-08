// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
// **The git repo variables will be changed to the users' git repositories manually in the Jenkins jobs**
def partsUnlimitedAppgitRepo = "PartsUnlimited"
def performanceTestsgitRepo = "dotnet-performance-tests"
def partsUnlimitedAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + partsUnlimitedAppgitRepo
def gatelingReferenceAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + performanceTestsgitRepo

// Jobs
def buildAndUnitTestJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Build_And_Unit_Tests")
def codeAnalysisJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Code_Analysis")
def deployJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Deploy")
def performanceTestsJob = freeStyleJob(projectFolderName + "/Parts_Unlimited_Performance_Tests")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/partsUnlimited_Application")

pipelineView.with{
    title('partsUnlimited Application Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/Parts_Unlimited_Build_And_Unit_Tests")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

// All jobs are tied to build on the Jenkins slave
// A default set of wrappers have been used for each job
// New jobs can be introduced into the pipeline as required

buildAndUnitTestJob.with{

  configure { project ->
    project / 'builders' / 'org.jenkinsci.plugins.DependencyCheck.DependencyCheckBuilder'(plugin: 'dependency-check-jenkins-plugin@1.4.4') {
      skipOnScmChange false
      skipOnUpstreamChange false
      scanpath '${WORKSPACE}'
      outdir
      datadir
      suppressionFile
      zipExtensions
      isAutoupdateDisabled false
      isVerboseLoggingEnabled false
      includeHtmlReports false
      useMavenArtifactsScanPath false
    }
  }

	description("partsUnlimited application build and unit test job.")
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
          |set -x
          |echo "Mount the source code into a container that will run the build and unit tests"
          |CONTNAME=adop-asp-build-${RANDOM}
          |docker run -t --name $CONTNAME \\
          |-v jenkins_slave_home:/jenkins_slave_home \\
          |subodhhatkar/adop-dotnet:1.0.1 \\
          |bash -c \\
          |"cd /jenkins_slave_home/${JOB_NAME}/src/PartsUnlimited.Models && \\
          |dotnet restore && \\
          |cd /jenkins_slave_home/${JOB_NAME}/src/PartsUnlimitedWebsite && \\
          |dotnet restore && \\
          |cd /jenkins_slave_home/${JOB_NAME}/test/PartsUnlimited.UnitTests && \\
          |dotnet restore && \\
          |dotnet test"
          |docker rm $CONTNAME
          |set +x
          |'''.stripMargin())
  }
	publishers{
		archiveArtifacts("**/*")
		downstreamParameterized{
		  trigger(projectFolderName + "/Parts_Unlimited_Code_Analysis"){
			condition("UNSTABLE_OR_BETTER")
			parameters{
			  predefinedProp("B",'${BUILD_NUMBER}')
			  predefinedProp("PARENT_BUILD", '${JOB_NAME}')
			}
		  }
		}
	}

  configure { project ->
    project / 'publishers' / 'org.jenkinsci.plugins.DependencyCheck.DependencyCheckPublisher'(plugin: 'dependency-check-jenkins-plugin@1.4.4') {
      healthy ''
      unHealthy ''
      thresholdLimit 'low'
      pluginName '[DependencyCheck]'
      defaultEncoding ''
      canRunOnFailed 'false'
      usePreviousBuildAsReference 'false'
      useStableBuildAsReference 'false'
      useDeltaValues 'false'
      thresholds (plugin: 'analysis-core@1.79') {
        unstableTotalAll ''
        unstableTotalHigh ''
        unstableTotalNormal ''
        unstableTotalLow ''
        unstableNewAll ''
        unstableNewHigh ''
        unstableNewNormal ''
        unstableNewLow ''
        failedTotalAll ''
        failedTotalHigh '1'
        failedTotalNormal ''
        failedTotalLow ''
        failedNewAll ''
        failedNewHigh ''
        failedNewNormal ''
        failedNewLow ''
      }
      shouldDetectModules false
      dontComputeNew true
      doNotResolveRelativePaths false
      pattern ''
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
    copyArtifacts('Parts_Unlimited_Build_And_Unit_Tests') {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''
          |set -x
          |printf "\n\n\n\nWARNING!!!!!\n\n The SonarQube step won't currently run c# analysis on Linux so the below step has been set to look for Java i.e. it is useless!\n\n \\
          |http://stackoverflow.com/questions/38852081/analyse-net-code-with-sonarqube-on-linux-platform --Still waiting on Microsoft\n\n"
          |set +x
          |'''.stripMargin())
  }
  publishers{
    groovyPostBuild{
      script("manager.buildUnstable()")
      sandbox(true)
    }
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
    copyArtifacts('Parts_Unlimited_Build_And_Unit_Tests') {
        buildSelector {
          buildNumber('${B}')
	}
    }
    shell('''
          |set -x
          |echo "Deploying..."
          |CONTNAME=asplinux
          |docker kill $CONTNAME || true
          |docker rm $CONTNAME || true
          |docker run -d --name $CONTNAME \\
          |-v jenkins_slave_home:/jenkins_slave_home \\
          |-p 8000:5000 \\
          |subodhhatkar/adop-dotnet:1.0.1 \\
          |bash -c \\
          |"cd /jenkins_slave_home/${JOB_NAME}/src/PartsUnlimited.Models && \\
          |dotnet restore && \\
          |cd /jenkins_slave_home/${JOB_NAME}/src/PartsUnlimitedWebsite && \\
          |dotnet restore && \\
          |dotnet run"
          |echo "Application running on port http://*:8000.."
          |set +x
          |'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Parts_Unlimited_Performance_Tests"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

performanceTestsJob.with {
    description("Run gatling performance tests tests for the dotnet app")
    parameters{
        stringParam("B",'',"Parent build number")
        stringParam("PARENT_BUILD",'',"Parent build name")
        stringParam("MAX_RESPONSE_TIME","7500","Maximum response time for performance tests (in milisec)")
    }
    wrappers {
        preBuildCleanup()
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
        env('PROJECT_NAME_KEY',projectFolderName.toLowerCase().replace("/", "-"))
        groovy("matcher = JENKINS_URL =~ /http:\\/\\/(.*?)\\/jenkins.*/; def map = [STACK_IP: matcher[0][1]]; return map;")
    }
    label("docker")
    scm {
        git {
            remote {
                url(gatelingReferenceAppGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    steps {
        shell('''
               |set -x
               |sleep 45
               |CONTAINER_IP=$(docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' asplinux )
               |sed -i "s/###TOKEN_VALID_URL###/http:\\/\\/localhost:8000/g" ${WORKSPACE}/src/test/scala/default/RecordedSimulation.scala
               |sed -i "s/###TOKEN_RESPONSE_TIME###/${MAX_RESPONSE_TIME}/g" ${WORKSPACE}/src/test/scala/default/RecordedSimulation.scala
               |set +x
               |'''.stripMargin())
    }
    steps{
        maven {
            goals("gatling:execute")
            mavenInstallation("ADOP Maven")
        }
    }
    configure{ myProject ->
        myProject / publishers << 'io.gatling.jenkins.GatlingPublisher'(plugin: "gatling@1.1.1") {
            enabled("true")
        }
    }
}
