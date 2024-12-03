pipeline {
	agent {
		kubernetes {
			label 'acute-buildtest'
			defaultContainer 'environment'
			yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: environment
    image: akurtakov/eclipse-acute-build-test-env:test
    imagePullPolicy: "Always"
    tty: true
    command: [ "cat" ]
    resources:
      requests:
        memory: "2.6Gi"
        cpu: "1.3"
      limits:
        memory: "2.6Gi"
        cpu: "1.3"
  - name: jnlp
    volumeMounts:
    - mountPath: /home/jenkins/.ssh
      name: volume-known-hosts
  volumes:
  - configMap:
      name: known-hosts
    name: volume-known-hosts
"""
		}
	}
	options {
		timeout(time: 60, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr:'10'))
		disableConcurrentBuilds(abortPrevious: true)
	}
	environment {
	    DOTNET_SKIP_FIRST_TIME_EXPERIENCE="true"
	    MAVEN_OPTS="-Xms256m -Xmx2048m"
	    M2_REPO="$WORKSPACE/m2-repo"
	}
	stages {
		stage('Test and initiate .NET Core') {
			steps {
			  sh 'mkdir dotnet-init && \
				  cd dotnet-init && \
					dotnet --version && \
					dotnet new console && \
					dotnet restore && \
					dotnet run'
			}
		}
		stage('Build') {
			steps {
				withCredentials([file(credentialsId: 'secret-subkeys.asc', variable: 'KEYRING'), string(credentialsId: 'gpg-passphrase', variable: 'KEYRING_PASSPHRASE')]) {
					wrap([$class: 'Xvnc', useXauthority: true]) {
						sh 'mvn clean verify -B -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true -Psign -Dtycho.pgp.signer.bc.secretKeys="${KEYRING}" -Dgpg.passphrase="${KEYRING_PASSPHRASE}"'
					}
				}
			}
			post {
				always {
					archiveArtifacts artifacts: '*/target/work/configuration/*.log,*/target/work/data/.metadata/.log, repository/target/repository/**', fingerprint: false
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
				container('jnlp') {
					sshagent (['projects-storage.eclipse.org-bot-ssh']) {
						sh 'ssh genie.acute@projects-storage.eclipse.org rm -rf /home/data/httpd/download.eclipse.org/acute/snapshots'
						sh 'ssh genie.acute@projects-storage.eclipse.org mkdir -p /home/data/httpd/download.eclipse.org/acute/snapshots'
						sh 'scp -r repository/target/repository/* genie.acute@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/acute/snapshots'
					}
				}
			}
		}
	}
}
