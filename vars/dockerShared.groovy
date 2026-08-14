def call(Map config = [:]) {
    return this
}


/**
 * Clone a Git repository
 */
def cloneRepo(
        String repoUrl,
        String branch = 'main',
        String dest = 'source'
) {

    echo "Cloning ${repoUrl}@${branch} to ${dest}"

    if (isUnix()) {

        sh """
            rm -rf '${dest}'
            git clone --branch '${branch}' '${repoUrl}' '${dest}'
        """

        return
    }

    bat """
        if exist '${dest}' rmdir /s /q '${dest}'
        git clone --branch '${branch}' '${repoUrl}' '${dest}'
    """
}


/**
 * Load a Dockerfile from the shared library resources
 */
def writeDockerfileResource(
        String resourcePath,
        String outName
) {

    def content = libraryResource(resourcePath)

    writeFile(
            file: outName,
            text: content
    )

    return outName
}


/**
 * Build Docker image
 */
def buildImage(
        String image,
        String dockerResourcePath,
        String contextDir = '.'
) {

    def dfName = dockerResourcePath.tokenize('/').last()

    writeDockerfileResource(
            dockerResourcePath,
            dfName
    )

    echo "Building Docker image: ${image}"

    if (isUnix()) {

        sh """
            docker build \
                -t '${image}' \
                -f '${dfName}' \
                '${contextDir}'
        """

        return
    }

    bat """
        docker build ^
            -t "${image}" ^
            -f "${dfName}" ^
            "${contextDir}"
    """
}


/**
 * Push Docker image to registry
 */
def pushImage(
        String image,
        String registry = null
) {

    def dockerRegistry = registry ?: env.DOCKER_REGISTRY

    if (!dockerRegistry) {
        error "DOCKER_REGISTRY is not configured"
    }

    def fullImage = "${dockerRegistry}/${image}"

    echo "Pushing Docker image: ${fullImage}"

    withCredentials([
            usernamePassword(
                    credentialsId: 'docker-registry-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASSWORD'
            )
    ]) {

        if (isUnix()) {

            sh """
                set -e

                echo "\$DOCKER_PASSWORD" | docker login \
                    -u "\$DOCKER_USER" \
                    --password-stdin

                docker tag '${image}' '${fullImage}'

                docker push '${fullImage}'
            """

        } else {

            bat """
                echo %DOCKER_PASSWORD% | docker login ^
                    -u %DOCKER_USER% ^
                    --password-stdin

                docker tag "${image}" "${fullImage}"

                docker push "${fullImage}"
            """
        }
    }

    return fullImage
}


/**
 * Deploy Docker container
 */
def deployContainer(
        String image,
        String name,
        Map options = [:]
) {

    def port = options.get(
            'port',
            ''
    )

    def envs = options.get(
            'env',
            [:]
    )

    def envArgs = envs.collect { k, v ->
        "-e ${k}=${v}"
    }.join(' ')

    def portArg = port ? "-p ${port}" : ""

    echo "Deploying container: ${name}"

    if (isUnix()) {

        sh """
            docker rm -f '${name}' 2>/dev/null || true

            docker run -d \
                --name '${name}' \
                ${envArgs} \
                ${portArg} \
                '${image}'
        """

        return
    }

    bat """
        docker rm -f "${name}" 2>NUL || exit /b 0

        docker run -d ^
            --name "${name}" ^
            ${envArgs} ^
            ${portArg} ^
            "${image}"
    """
}


/**
 * SonarQube scan
 */
def scanSonarqube(
        String projectKey,
        String sources = '.'
) {

    if (!env.SONAR_HOST) {

        echo "SONAR_HOST not configured; skipping SonarQube scan"

        return
    }

    withCredentials([
            string(
                    credentialsId: 'SONAR_TOKEN',
                    variable: 'SONAR_TOKEN'
            )
    ]) {

        echo "Running SonarQube scan for ${projectKey}"

        if (isUnix()) {

            sh """
                set -e

                sonar-scanner \
                    -Dsonar.projectKey='${projectKey}' \
                    -Dsonar.sources='${sources}' \
                    -Dsonar.host.url='${SONAR_HOST}' \
                    -Dsonar.token="\$SONAR_TOKEN"
            """

            return
        }

        bat """
            sonar-scanner ^
                -Dsonar.projectKey="${projectKey}" ^
                -Dsonar.sources="${sources}" ^
                -Dsonar.host.url="%SONAR_HOST%" ^
                -Dsonar.token="%SONAR_TOKEN%"
        """
    }
}


/**
 * Send Telegram notification
 *
 * IMPORTANT:
 * chatId is passed explicitly from Jenkinsfile.
 */
def sendTelegram(
        String message,
        String chatId = null
) {

    // Use explicitly supplied chat ID first.
    // If not supplied, fall back to Jenkins environment variable.
    def telegramChatId = chatId ?: env.TELEGRAM_CHAT_ID

    if (!telegramChatId) {

        error "TELEGRAM_CHAT_ID not configured"
    }

    echo "Sending Telegram notification..."

    withCredentials([
            string(
                    credentialsId: 'telegram-bot',
                    variable: 'TELEGRAM_TOKEN'
            )
    ]) {

        def payload = groovy.json.JsonOutput.toJson([
                chat_id: telegramChatId,
                text: message
        ])

        if (isUnix()) {

            writeFile(
                    file: 'telegram-payload.json',
                    text: payload
            )

            try {

                sh '''
                    set -e

                    curl \
                        --fail \
                        --silent \
                        --show-error \
                        --request POST \
                        --header "Content-Type: application/json" \
                        --data-binary @telegram-payload.json \
                        "https://api.telegram.org/bot${TELEGRAM_TOKEN}/sendMessage"
                '''

            } finally {

                sh '''
                    rm -f telegram-payload.json
                '''
            }

            return
        }

        bat """
            powershell -NoProfile -Command ^
                "\$payload = Get-Content -Raw 'telegram-payload.json'; ^
                Invoke-RestMethod ^
                    -Uri 'https://api.telegram.org/bot%TELEGRAM_TOKEN%/sendMessage' ^
                    -Method Post ^
                    -Body \$payload ^
                    -ContentType 'application/json'"
        """
    }
}