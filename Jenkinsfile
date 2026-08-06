pipeline {
    agent any

    parameters {
        string(name: 'REPO_URL', defaultValue: 'https://github.com/Neueda-Learning/11-102-404Founders.git', description: 'Git repository URL for standalone Pipeline jobs')
        string(name: 'REPO_BRANCH', defaultValue: 'main', description: 'Branch to checkout when REPO_URL is provided')
        string(name: 'GIT_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins credentials ID for private repositories')
    }

    environment {
        COMPOSE_FILE = 'docker-compose.yml'
        COMPOSE_CI_FILE = 'docker-compose.ci.yml'
        COMPOSE_FILES = '-f docker-compose.yml'
        BACKEND_DIR = 'backend'
    }

    stages {
        stage('Checkout Source') {
            steps {
                script {
                    if (binding.hasVariable('scm') && scm) {
                        checkout scm
                    } else {
                        def sourceUrl = params.REPO_URL?.trim() ?: env.GIT_URL?.trim()

                        if (sourceUrl) {
                            if (params.GIT_CREDENTIALS_ID?.trim()) {
                                git branch: params.REPO_BRANCH, url: sourceUrl, credentialsId: params.GIT_CREDENTIALS_ID
                            } else {
                                git branch: params.REPO_BRANCH, url: sourceUrl
                            }
                        } else if (fileExists('backend/pom.xml')) {
                            echo 'No SCM context detected. Using existing workspace content.'
                        } else {
                            error('No SCM context available and no repository URL provided. Set REPO_URL (or env.GIT_URL), or configure this job as Multibranch/Pipeline from SCM.')
                        }
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

        stage('Resolve Compose Command') {
            steps {
                script {
                    if (isUnix()) {
                        def hasComposeV2 = sh(script: 'docker compose version >/dev/null 2>&1', returnStatus: true) == 0
                        if (hasComposeV2) {
                            env.DOCKER_COMPOSE_CMD = 'docker compose'
                        } else {
                            def hasComposeV1 = sh(script: 'docker-compose --version >/dev/null 2>&1', returnStatus: true) == 0
                            if (hasComposeV1) {
                                env.DOCKER_COMPOSE_CMD = 'docker-compose'
                            } else {
                                error('Neither `docker compose` nor `docker-compose` is available on this Jenkins agent.')
                            }
                        }
                    } else {
                        def hasComposeV2 = bat(script: 'docker compose version >NUL 2>&1', returnStatus: true) == 0
                        if (hasComposeV2) {
                            env.DOCKER_COMPOSE_CMD = 'docker compose'
                        } else {
                            def hasComposeV1 = bat(script: 'docker-compose --version >NUL 2>&1', returnStatus: true) == 0
                            if (hasComposeV1) {
                                env.DOCKER_COMPOSE_CMD = 'docker-compose'
                            } else {
                                error('Neither `docker compose` nor `docker-compose` is available on this Jenkins agent.')
                            }
                        }
                    }

                    if (!fileExists(env.COMPOSE_FILE)) {
                        error("Compose file not found: ${env.COMPOSE_FILE}")
                    }

                    // Use CI override only when present in checked-out source.
                    if (fileExists(env.COMPOSE_CI_FILE)) {
                        env.COMPOSE_FILES = "-f ${env.COMPOSE_FILE} -f ${env.COMPOSE_CI_FILE}"
                    } else {
                        env.COMPOSE_FILES = "-f ${env.COMPOSE_FILE}"
                        echo "Optional compose override not found: ${env.COMPOSE_CI_FILE}. Continuing with ${env.COMPOSE_FILE}."
                    }

                    echo "Using Compose command: ${env.DOCKER_COMPOSE_CMD}"
                    echo "Using compose files: ${env.COMPOSE_FILES}"
                }
            }
        }

        stage('Stop Existing Containers') {
            steps {
                script {
                    if (isUnix()) {
                        sh "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} down || true"
                    } else {
                        bat "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} down"
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    if (isUnix()) {
                        sh "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} build --no-cache"
                    } else {
                        bat "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} build --no-cache"
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    if (isUnix()) {
                        sh "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} up -d"
                    } else {
                        bat "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} up -d"
                    }
                }
            }
        }

        stage('Verify') {
            steps {
                script {
                    if (isUnix()) {
                        sh "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} ps"
                    } else {
                        bat "${env.DOCKER_COMPOSE_CMD} ${env.COMPOSE_FILES} ps"
                    }
                }
            }
        }
    }
}

