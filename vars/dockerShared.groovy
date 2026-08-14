def call(Map config = [:]) {
    return this
}

def cloneRepo(String repoUrl, String branch = 'main', String dest = 'source') {
    echo "Cloning ${repoUrl}@${branch} to ${dest}"

    if (isUnix()) {
        sh "rm -rf ${dest} && git clone --branch ${branch} ${repoUrl} ${dest}"
        return
    }

    bat "if exist ${dest} rmdir /s /q ${dest} && git clone --branch ${branch} ${repoUrl} ${dest}"
}

def writeDockerfileResource(String resourcePath, String outName) {
    def content = libraryResource(resourcePath)
    writeFile file: outName, text: content
    return outName
}

def buildImage(String image, String dockerResourcePath, String contextDir = '.') {
    def dfName = dockerResourcePath.tokenize('/').last()

    writeDockerfileResource(dockerResourcePath, dfName)

    if (isUnix()) {
        sh "docker build -t ${image} -f ${dfName} ${contextDir}"
        return
    }

    bat "docker build -t ${image} -f ${dfName} ${contextDir}"
}

def pushImage(String image, String registry = null) {
    def dockerRegistry = registry ?: env.DOCKER_REGISTRY

    if (!dockerRegistry) {
        error "DOCKER_REGISTRY is not configured"
    }

    def fullImage = "${dockerRegistry}/${image}"

    if (isUnix()) {
        sh "docker tag ${image} ${fullImage} && docker push ${fullImage}"
    } else {
        bat "docker tag ${image} ${fullImage} && docker push ${fullImage}"
    }

    return fullImage
}

def deployContainer(String image, String name, Map options = [:]) {
    def port = options.get('port', '')
    def envs = options.get('env', [:])

    def envArgs = envs.collect { k, v ->
        "-e ${k}=${v}"
    }.join(' ')

    def portArg = port ? "-p ${port}" : ""

    if (isUnix()) {
        sh "docker rm -f ${name} || true; docker run -d --name ${name} ${envArgs} ${portArg} ${image}"
        return
    }

    bat "docker rm -f ${name} || exit 0 & docker run -d --name ${name} ${envArgs} ${portArg} ${image}"
}

def scanSonarqube(String projectKey, String sources = '.') {

    if (!env.SONAR_HOST) {
        echo "SONAR_HOST not configured; skipping SonarQube scan"
        return
    }

    if (!env.SONAR_CREDENTIAL_ID) {
        error "SONAR_CREDENTIAL_ID not configured"
    }

    withCredentials([
            string(
                    credentialsId: env.SONAR_CREDENTIAL_ID,
                    variable: 'SONAR_TOKEN'
            )
    ]) {

        if (isUnix()) {
            sh """
                sonar-scanner \
                -Dsonar.projectKey=${projectKey} \
                -Dsonar.sources=${sources} \
                -Dsonar.host.url=${SONAR_HOST} \
                -Dsonar.token=\$SONAR_TOKEN
            """
            return
        }

        bat """
            sonar-scanner ^
            -Dsonar.projectKey=${projectKey} ^
            -Dsonar.sources=${sources} ^
            -Dsonar.host.url=%SONAR_HOST% ^
            -Dsonar.token=%SONAR_TOKEN%
        """
    }
}

def sendTelegram(String message) {

    if (!env.TELEGRAM_BOT_CREDENTIAL_ID) {
        error "TELEGRAM_BOT_CREDENTIAL_ID not configured"
    }

    if (!env.TELEGRAM_CHAT_ID) {
        error "TELEGRAM_CHAT_ID not configured"
    }

    withCredentials([
            string(
                    credentialsId: env.TELEGRAM_BOT_CREDENTIAL_ID,
                    variable: 'TELEGRAM_TOKEN'
            )
    ]) {

        def payload = groovy.json.JsonOutput.toJson([
                chat_id: env.TELEGRAM_CHAT_ID,
                text: message
        ])

        if (isUnix()) {
            sh """
                curl -s -X POST \
                -H 'Content-Type: application/json' \
                -d '${payload}' \
                "https://api.telegram.org/bot\$TELEGRAM_TOKEN/sendMessage"
            """
            return
        }

        bat """
            powershell -Command "Invoke-RestMethod \
            -Uri 'https://api.telegram.org/bot%TELEGRAM_TOKEN%/sendMessage' \
            -Method Post \
            -Body '${payload}' \
            -ContentType 'application/json'"
        """
    }
}