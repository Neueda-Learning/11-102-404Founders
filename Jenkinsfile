pipeline {
    agent any

    environment {
        COMPOSE_FILE = 'docker-compose.yml'
        BACKEND_DIR = 'backend'
    }

    stages {
        stage('Checkout Source') {
            steps {
                checkout scm
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

