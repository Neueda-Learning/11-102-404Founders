pipeline {
    agent any

    parameters {
        string(name: 'REPO_URL', defaultValue: '', description: 'Optional: Git repository URL for standalone Pipeline jobs')
        string(name: 'REPO_BRANCH', defaultValue: 'main', description: 'Branch to checkout when REPO_URL is provided')
        string(name: 'GIT_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins credentials ID for private repositories')
    }

    environment {
        COMPOSE_FILE = 'docker-compose.yml'
        BACKEND_DIR = 'backend'
    }

    stages {
        stage('Checkout Source') {
            steps {
                script {
                    if (binding.hasVariable('scm') && scm) {
                        checkout scm
                    } else if (params.REPO_URL?.trim()) {
                        if (params.GIT_CREDENTIALS_ID?.trim()) {
                            git branch: params.REPO_BRANCH, url: params.REPO_URL, credentialsId: params.GIT_CREDENTIALS_ID
                        } else {
                            git branch: params.REPO_BRANCH, url: params.REPO_URL
                        }
                    } else if (fileExists('backend/pom.xml')) {
                        echo 'No SCM context detected. Using existing workspace content.'
                    } else {
                        error('No SCM context available. Configure this job as Multibranch/Pipeline from SCM or provide REPO_URL parameter.')
                    }
                }
            }
        }

        stage('Build Spring Boot') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn -f backend/pom.xml clean package -DskipTests'
                    } else {
                        bat 'backend\\mvnw.cmd clean package -DskipTests'
                    }
                }
            }
        }

        stage('Stop Existing Containers') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'docker compose -f docker-compose.yml down || true'
                    } else {
                        bat 'docker compose -f docker-compose.yml down'
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'docker compose -f docker-compose.yml build --no-cache'
                    } else {
                        bat 'docker compose -f docker-compose.yml build --no-cache'
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'docker compose -f docker-compose.yml up -d'
                    } else {
                        bat 'docker compose -f docker-compose.yml up -d'
                    }
                }
            }
        }

        stage('Verify') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'docker compose -f docker-compose.yml ps'
                    } else {
                        bat 'docker compose -f docker-compose.yml ps'
                    }
                }
            }
        }
    }
}

