pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timestamps()
    }

    parameters {
        booleanParam(name: 'RUN_FRONTEND', defaultValue: true, description: 'Build/test frontend in this pipeline run')
        booleanParam(name: 'PUSH_IMAGES', defaultValue: false, description: 'Push Docker images when branch is main')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Deploy stack when branch is main')
    }

    environment {
        APP_IMAGE = 'team-skeleton'
        FRONTEND_IMAGE = 'frontend-skeleton'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        // Optional external registry configuration:
        // DOCKER_REGISTRY (example: ghcr.io)
        // DOCKER_NAMESPACE (example: acme)
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out branch ${env.BRANCH_NAME}"
                checkout scm
            }
        }

        stage('Backend Test and Build') {
            steps {
                dir('team') {
                    sh 'mvn -B clean verify'
                    sh 'mvn -B package -DskipTests'
                }
            }
        }

        stage('Frontend Test and Build') {
            when {
                allOf {
                    expression { return params.RUN_FRONTEND }
                    expression { return fileExists('frontend/package.json') }
                }
            }
            steps {
                dir('frontend') {
                    sh '''
                        set -euo pipefail

                        if command -v npm >/dev/null 2>&1; then
                            echo "Using host npm: $(npm --version)"
                            if [ -f package-lock.json ]; then
                                npm ci
                            else
                                npm install
                            fi
                            npm run test -- --watch=false
                            npm run build
                        else
                            echo "Host npm not found; running frontend build in Node Docker container"
                            docker run --rm \
                                -u "$(id -u):$(id -g)" \
                                -e HOME=/tmp \
                                -e NPM_CONFIG_CACHE=/tmp/.npm \
                                -e NPM_CONFIG_UPDATE_NOTIFIER=false \
                                -v "$PWD:/workspace" \
                                -w /workspace \
                                node:22-alpine \
                                sh -lc "set -euo pipefail; if [ -f package-lock.json ]; then npm ci; else npm install; fi; npm run test -- --watch=false; npm run build"
                        fi
                    '''
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                sh "docker build -t ${APP_IMAGE}:${IMAGE_TAG} -t ${APP_IMAGE}:latest -f team/Dockerfile team"
                script {
                    if (params.RUN_FRONTEND && fileExists('frontend/Dockerfile.frontend')) {
                        sh "docker build -t ${FRONTEND_IMAGE}:${IMAGE_TAG} -t ${FRONTEND_IMAGE}:latest -f frontend/Dockerfile.frontend frontend"
                    } else {
                        echo 'Skipping frontend image build.'
                    }
                }
            }
        }

        stage('Push Images') {
            when {
                allOf {
                    branch 'main'
                    expression { return params.PUSH_IMAGES }
                    expression { return env.DOCKER_REGISTRY?.trim() }
                    expression { return env.DOCKER_NAMESPACE?.trim() }
                }
            }
            steps {
                sh '''
                    docker tag ${APP_IMAGE}:${IMAGE_TAG} ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${APP_IMAGE}:${IMAGE_TAG}
                    docker tag ${APP_IMAGE}:latest ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${APP_IMAGE}:latest
                    docker push ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${APP_IMAGE}:${IMAGE_TAG}
                    docker push ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${APP_IMAGE}:latest
                '''
                script {
                    if (params.RUN_FRONTEND) {
                        sh '''
                            docker tag ${FRONTEND_IMAGE}:${IMAGE_TAG} ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${FRONTEND_IMAGE}:${IMAGE_TAG}
                            docker tag ${FRONTEND_IMAGE}:latest ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${FRONTEND_IMAGE}:latest
                            docker push ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${FRONTEND_IMAGE}:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/${DOCKER_NAMESPACE}/${FRONTEND_IMAGE}:latest
                        '''
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                allOf {
                    branch 'main'
                    expression { return params.DEPLOY }
                }
            }
            steps {
                sh 'docker compose up -d --build app postgres-db frontend'
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded for branch ${env.BRANCH_NAME}"
        }
        failure {
            echo "Pipeline failed for branch ${env.BRANCH_NAME}"
        }
        always {
            sh 'docker image prune -f || true'
        }
    }
}
