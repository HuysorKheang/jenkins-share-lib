@Library('my-shared-lib') _
pipeline {
  agent any
  parameters {
    string(name:'REPO_URL', defaultValue:'https://github.com/example/repo.git', description:'Repository to clone')
    string(name:'BRANCH', defaultValue:'main', description:'Branch to build')
    string(name:'IMAGE_NAME', defaultValue:'myapp', description:'Local image name')
    string(name:'REGISTRY', defaultValue:'myregistry.example.com', description:'Docker registry')
    string(name:'DOCKERFILE', defaultValue:'dockerfiles/reactjs.Dockerfile', description:'Path to Dockerfile resource in library')
  }
  stages {
    stage('Clone') {
      steps {
        script {
          dockerShared.cloneRepo(params.REPO_URL, params.BRANCH, 'source')
        }
      }
    }
    stage('Build') {
      steps {
        dir('source') {
          script {
            dockerShared.buildImage(params.IMAGE_NAME, params.DOCKERFILE, '.')
          }
        }
      }
    }
    stage('Push') {
      steps {
        script {
          def full = dockerShared.pushImage(params.IMAGE_NAME, params.REGISTRY)
          echo "Pushed ${full}"
        }
      }
    }
    stage('Deploy') {
      steps {
        script {
          dockerShared.deployContainer("${params.REGISTRY}/${params.IMAGE_NAME}", params.IMAGE_NAME, [port:'80:3000'])
        }
      }
    }
    stage('Post') {
      steps {
        script {
          dockerShared.sendTelegram("Deployment complete for ${params.IMAGE_NAME}")
        }
      }
    }
  }
}
