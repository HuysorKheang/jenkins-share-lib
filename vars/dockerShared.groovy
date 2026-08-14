def cloneRepo(
        String url,
        String branch = 'main',
        String credentialsId = null,
        String directory = null
) {

    def targetDirectory = directory ?: 'source'

    dir(targetDirectory) {

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
}


def buildReact(
        String imageName,
        String imageTag,
        String projectDirectory
) {

    buildImageFromResource(
            imageName,
            imageTag,
            'docker/reactjs.Dockerfile',
            projectDirectory
    )
}


def buildSpring(
        String imageName,
        String imageTag,
        String projectDirectory
) {

    buildImageFromResource(
            imageName,
            imageTag,
            'docker/spring.Dockerfile',
            projectDirectory
    )
}


def buildImageFromResource(
        String imageName,
        String imageTag,
        String dockerfileResource,
        String buildContext
) {

    def dockerfile = libraryResource(dockerfileResource)

    def dockerfileName = dockerfileResource.tokenize('/').last()

    dir(buildContext) {

        writeFile(
                file: dockerfileName,
                text: dockerfile
        )

        try {

            sh """
                set -e

                echo "======================================"
                echo "Docker Build"
                echo "Image: ${imageName}:${imageTag}"
                echo "Dockerfile: ${dockerfileName}"
                echo "Context: ${buildContext}"
                echo "======================================"

                docker build \
                    -f ${dockerfileName} \
                    -t ${imageName}:${imageTag} \
                    .
            """

        } finally {

            sh """
                rm -f ${dockerfileName}
            """
        }
    }
}


def pushImage(
        String imageName,
        String imageTag,
        String registry = ''
) {

    def fullImage = registry
            ? "${registry}/${imageName}:${imageTag}"
            : "${imageName}:${imageTag}"

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
            echo "Image: ${fullImage}"
            echo "======================================"

            echo "\${DOCKER_PASS}" | docker login \
                -u "\${DOCKER_USER}" \
                --password-stdin

            docker push ${fullImage}

            docker logout || true
        """
    }
}


def deployContainer(
        String containerName,
        String image,
        String hostPort,
        String containerPort
) {

    sh """
        set -e

        echo "======================================"
        echo "Deploy Container"
        echo "Container: ${containerName}"
        echo "Image: ${image}"
        echo "Port: ${hostPort}:${containerPort}"
        echo "======================================"

        docker rm -f ${containerName} || true

        docker run -d \
            --name ${containerName} \
            --restart unless-stopped \
            -p ${hostPort}:${containerPort} \
            ${image}

        docker ps \
            --filter "name=${containerName}"
    """
}


def scanSonarqube(
        String projectKey,
        String projectDirectory
) {

    withSonarQubeEnv('SonarQube') {

        withCredentials([
                string(
                        credentialsId: 'SONAR_TOKEN',
                        variable: 'SONAR_TOKEN'
                )
        ]) {

            def scannerHome = tool 'SonarScanner'

            dir(projectDirectory) {

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


def waitForSonarQualityGate(
        int timeoutMinutes = 5
) {

    timeout(
            time: timeoutMinutes,
            unit: 'MINUTES'
    ) {

        def result = waitForQualityGate()

        if (result.status != 'OK') {

            error(
                    "SonarQube Quality Gate failed: ${result.status}"
            )
        }
    }
}


def sendTelegram(
        String message
) {

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

        sh '''
            set +x

            echo "Sending Telegram notification..."

            curl -sS \
                -X POST \
                "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
                --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
                --data-urlencode "text=${TELEGRAM_MESSAGE}"

            echo "Telegram notification sent."
        ''',
                returnStatus: false
    }
}