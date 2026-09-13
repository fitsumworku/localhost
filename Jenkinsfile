// Jenkinsfile only pertains to cleaning, packaging, and building the starter folder into a docker image
// Not related to the separate dockerfile for the postgres instance
pipeline {

    agent any
    stages {

        // Checkout the github files
        stage('Checkout') {
            steps {
                echo "Checking out code from branch ${env.BRANCH_NAME}"
                checkout scm
            }
        }

        // Building image stage
        stage('Build Image') {
            steps {
                sh 'mvn -B clean package -DskipTests -f starter/pom.xml'
                sh 'docker build -t team-skeleton:latest -f starter/Dockerfile starter/'
            }
        }
        
        // To create a new docker container on the starter folder
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm team-skeleton:latest'
            }
        }

        stage('Push to Registry') {
            // Only on main branch
            when {
                branch 'main'
            }
            steps {
                echo "Pushing image to registry..."
                // sh 'docker push ${REGISTRY}/${DOCKER_IMAGE}:${BUILD_NUMBER}'
                // sh 'docker push ${REGISTRY}/${DOCKER_IMAGE}:latest'
            }
        }
        stage('Deploy to Production') {
            // Only on main branch
            when {
                branch 'main'
            }
            steps {
                echo "Deploying to production..."
                // Add your deployment commands here
                // Examples:
                // sh 'kubectl set image deployment/app app=${REGISTRY}/${DOCKER_IMAGE}:${BUILD_NUMBER}'
                // sh 'docker-compose -f docker-compose.prod.yml up -d'
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded for branch: ${env.BRANCH_NAME}"
        }
        failure {
            echo "Pipeline failed for branch: ${env.BRANCH_NAME}"
        }
    }
}
