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
                    set -e

                    echo "======================================"
                    echo "SonarQube Scan"
                    echo "Project: ${projectKey}"
                    echo "======================================"

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

    dir(buildContext) {

        sh """
            set -e

            echo "======================================"
            echo "Docker Build"
            echo "Image: ${imageName}"
            echo "Dockerfile: ${dockerfile}"
            echo "Context: ${buildContext}"
            echo "======================================"

            docker build \
                -f ${dockerfile} \
                -t ${imageName} \
                .
        """
    }
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

            echo "======================================"
            echo "Docker Push"
            echo "Image: ${imageName}"
            echo "======================================"

            echo "\${DOCKER_PASS}" | docker login \
                -u "\${DOCKER_USER}" \
                --password-stdin

            docker push ${imageName}

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
        error "Port mapping is required for ${containerName}"
    }

    sh """
        set -e

        echo "======================================"
        echo "Deploying Container"
        echo "Container: ${containerName}"
        echo "Image: ${image}"
        echo "Port: ${config.port}"
        echo "======================================"

        docker rm -f ${containerName} || true

        docker run -d \
            --name ${containerName} \
            --restart unless-stopped \
            -p ${config.port} \
            ${image}

        echo "Container deployed successfully."
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

                echo "======================================"
                echo "Sending Telegram notification..."
                echo "======================================"

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