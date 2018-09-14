pipeline {
	agent any
	options {
		buildDiscarder(logRotator(numToKeepStr:'10'))
	}
	stages {
		stage('Prepare') {
			steps {
				git url: 'https://github.com/eclipse/aCute.git'
				cleanWs()
				checkout scm
			}
		}
		stage('Build') {
			steps {
				wrap([$class: 'Xvnc', useXauthority: true]) {
					withEnv(["PATH+DOTNET=/shared/common/dotnet-sdk-2.0.0-linux-x64", "DOTNET_SKIP_FIRST_TIME_EXPERIENCE=true"]) {
						withMaven(maven: 'apache-maven-latest', jdk: 'jdk1.8.0-latest', mavenLocalRepo: '.repository') {
							sh 'mvn clean verify -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true -PpackAndSign'
						}
					}
				}
			}
			post {
				success {
					junit '*/target/surefire-reports/TEST-*.xml' 
				}
			}
		}
		stage('Deploy') {
			when {
				branch 'master'
				// TODO deploy all branch from Eclipse.org Git repo
			}
			steps {
				// TODO compute the target URL (snapshots) according to branch name (0.5-snapshots...)
				sh 'rm -rf /home/data/httpd/download.eclipse.org/acute/snapshots'
				sh 'mkdir -p /home/data/httpd/download.eclipse.org/acute/snapshots'
				sh 'cp -r repository/target/repository/* /home/data/httpd/download.eclipse.org/acute/snapshots'
				sh 'zip -R /home/data/httpd/download.eclipse.org/acute/snapshots/repository.zip repository/target/repository/*'
			}
		}
	}
}
