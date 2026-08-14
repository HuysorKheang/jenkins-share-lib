def cloneRepo(String url, String branch, String credentialsId = null) {
    if (credentialsId) {
        git branch: branch, url: url, credentialsId: credentialsId
    } else {
        git branch: branch, url: url
    }
}

def scanSonarQube(String projectKey, String sonarHostUrl = 'http://sonarqube:9000',
                  String scannerToolName = 'SonarScanner', String sonarServerName = 'SonarQube') {
    withSonarQubeEnv(sonarServerName) {
        withCredentials([string(credentialsId: 'SONAR_TOKEN', variable: 'SONAR_TOKEN')]) {
            def scannerHome = tool scannerToolName
            sh """
                ${scannerHome}/bin/sonar-scanner \\
                    -Dsonar.projectKey=${projectKey} \\
                    -Dsonar.host.url=${sonarHostUrl} \\
                    -Dsonar.login=\${SONAR_TOKEN}
            """
        }
    }
}

def waitForSonarQualityGate(int timeoutMinutes = 5) {
    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
            error "Pipeline aborted: SonarQube Quality Gate failed with status ${qg.status}"
        }
    }
}

def buildDockerImage(String imageName, String imageTag, String dockerfileType, String buildContext, String reactDockerfile, String springDockerfile) {
    def dockerfileName = "${dockerfileType}.Dockerfile"
    def content = (dockerfileType == 'reactjs') ? reactDockerfile : springDockerfile
    writeFile file: dockerfileName, text: content

    sh """
        docker build -f ${dockerfileName} -t ${imageName}:${imageTag} ${buildContext}
    """
}

def pushDockerImage(String imageName, String imageTag, String registryUrl) {
    withCredentials([usernamePassword(
            credentialsId: 'docker-registry-creds',
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASS'
    )]) {
        sh """
            echo "\${DOCKER_PASS}" | docker login ${registryUrl} -u "\${DOCKER_USER}" --password-stdin
            docker push ${imageName}:${imageTag}
            docker logout ${registryUrl} || true
        """
    }
}

def deployContainer(String containerName, String imageName, String imageTag, String hostPort, String containerPort) {
    sh """
        docker rm -f ${containerName} || true
        docker run -d \\
            --name ${containerName} \\
            --restart unless-stopped \\
            -p ${hostPort}:${containerPort} \\
            ${imageName}:${imageTag}
    """
}
def sendTelegram(String message) {

    node {

        withCredentials([
                string(
                        credentialsId: 'telegram-bot',
                        variable: 'TELEGRAM_BOT_TOKEN'
                ),
                string(
                        credentialsId: 'TELEGRAM_CHAT_ID',
                        variable: 'TELEGRAM_CHAT_ID'
                )
        ]) {

            sh """
                curl -sS -X POST \\
                    "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \\
                    --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \\
                    --data-urlencode "text=${message}"
            """
        }
    }
}