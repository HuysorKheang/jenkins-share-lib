def cloneRepo(String url, String branch, String credentialsId = null) {

    if (credentialsId) {
        git(
                branch: branch,
                url: url,
                credentialsId: credentialsId
        )
    } else {
        git(
                branch: branch,
                url: url
        )
    }
}


def scanSonarqube(String projectKey, String projectPath) {

    withSonarQubeEnv('SonarQube') {

        withCredentials([
                string(
                        credentialsId: 'SONAR_TOKEN',
                        variable: 'SONAR_TOKEN'
                )
        ]) {

            def scannerHome = tool 'SonarScanner'

            dir(projectPath) {

                sh """
                    ${scannerHome}/bin/sonar-scanner \
                        -Dsonar.projectKey=${projectKey} \
                        -Dsonar.host.url=${SONAR_HOST} \
                        -Dsonar.token=\${SONAR_TOKEN}
                """
            }
        }
    }
}


def waitForSonarQualityGate(int timeoutMinutes = 5) {

    timeout(
            time: timeoutMinutes,
            unit: 'MINUTES'
    ) {

        def qg = waitForQualityGate()

        if (qg.status != 'OK') {
            error(
                    "Pipeline aborted: SonarQube Quality Gate failed with status ${qg.status}"
            )
        }
    }
}


def buildImage(
        String imageName,
        String dockerfile,
        String buildContext
) {

    sh """
        set -e

        echo "Building Docker image: ${imageName}"

        docker build \
            -f ${dockerfile} \
            -t ${imageName} \
            ${buildContext}
    """
}


def pushImage(String imageName) {

    withCredentials([
            usernamePassword(
                    credentialsId: 'docker-registry-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
            )
    ]) {

        sh """
            set -e

            echo "Logging in to Docker registry..."

            echo "\${DOCKER_PASS}" | docker login \
                -u "\${DOCKER_USER}" \
                --password-stdin

            echo "Pushing image: ${imageName}"

            docker push ${imageName}

            echo "Logging out..."

            docker logout || true
        """
    }
}


def deployContainer(
        String image,
        String containerName,
        Map config = [:]
) {

    if (!config.port) {
        error "Port mapping is required for container ${containerName}"
    }

    sh """
        set -e

        echo "Stopping old container..."

        docker rm -f ${containerName} || true

        echo "Starting container..."

        docker run -d \
            --name ${containerName} \
            --restart unless-stopped \
            -p ${config.port} \
            ${image}

        echo "Container started: ${containerName}"
    """
}


def sendTelegram(String message) {

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

        withEnv([
                "TELEGRAM_MESSAGE=${message}"
        ]) {

            sh '''
                set -e

                echo "Sending Telegram notification..."

                curl -sS \
                    -o /dev/null \
                    -w "HTTP Status: %{http_code}\\n" \
                    -X POST \
                    "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
                    --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
                    --data-urlencode "text=${TELEGRAM_MESSAGE}"

                echo "Telegram notification sent."
            '''
        }
    }
}