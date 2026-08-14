

def call(Map config = [:]) {
    return this
}

def cloneRepo(String repoUrl, String branch = 'main', String dest = 'source') {
    echo "Cloning ${repoUrl}@${branch} to ${dest}"
    if (isUnix()) {
        sh "rm -rf ${dest} && git clone --branch ${branch} ${repoUrl} ${dest}"
        return
    }

    bat "if (Test-Path ${dest}) { Remove-Item -Recurse -Force ${dest} }; git clone --branch ${branch} ${repoUrl} ${dest}"

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

def pushImage(String image, String registry) {
    def fullImage = "${registry}/${image}"
    if (isUnix()) {
        sh "docker tag ${image} ${fullImage} && docker push ${fullImage}"
    } else {
        bat "docker tag ${image} ${fullImage} & docker push ${fullImage}"
    }
    return fullImage
}

def deployContainer(String image, String name, Map options = [:]) {
    def port = options.get('port', '')
    def envs = options.get('env', [:])
    def envArgs = envs.collect { k, v -> "-e ${k}=${v}" }.join(' ')
    def portArg = port ? "-p ${port}" : ""
    if (isUnix()) {
        sh "docker rm -f ${name} || true; docker run -d --name ${name} ${envArgs} ${portArg} ${image}"
        return
    }
    bat "docker rm -f ${name} || exit 0 & docker run -d --name ${name} ${envArgs} ${portArg} ${image}"

}

def scanSonarqube(String projectKey, String sources = '.') {
    if (!env.SONAR_HOST || !env.SONAR_TOKEN) {
        echo "SONAR_HOST or SONAR_TOKEN not set; skipping SonarQube scan"
        return
    }
    if (isUnix()) {
        sh "sonar-scanner -Dsonar.projectKey=${projectKey} -Dsonar.sources=${sources} -Dsonar.host.url=${env.SONAR_HOST} -Dsonar.login=${env.SONAR_TOKEN}"
        return
    }
    bat "sonar-scanner -Dsonar.projectKey=${projectKey} -Dsonar.sources=${sources} -Dsonar.host.url=${env.SONAR_HOST} -Dsonar.login=${env.SONAR_TOKEN}"

}

def sendTelegram(String message, String webhook = null) {
    def url = webhook ?: env.TELEGRAM_WEBHOOK
    if (!url) {
        echo "TELEGRAM_WEBHOOK not set";
        return
    }
    def payload = "{\"text\":\"${message}\"}"
    if (isUnix()) {
        sh "curl -s -X POST -H 'Content-Type: application/json' -d '${payload}' ${url} || true"
        return
    }
    bat "powershell -Command \"Invoke-RestMethod -Uri '${url}' -Method Post -Body '${payload}' -ContentType 'application/json'\""

}
