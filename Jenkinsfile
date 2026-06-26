pipeline {
    agent any

    environment {
        AWS_REGION      = 'us-east-1'
        ECR_REGISTRY    = "${AWS_ACCOUNT_ID ?: 'YOUR_ACCOUNT_ID'}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_TAG       = "${env.BUILD_NUMBER}-${env.GIT_COMMIT[0..6]}"
        EKS_CLUSTER     = 'asms-prod-eks'
        K8S_NAMESPACE   = 'asms-prod'
        MAVEN_OPTS      = '-Xss512k -XX:+UseG1GC'
        HAS_AWS_CREDS   = "${env.AWS_ACCOUNT_ID ? 'true' : 'false'}"
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log --oneline -5'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn test --no-transfer-progress -pl user-service,support-service,payment-service,visitor-service'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build JARs') {
            steps {
                sh 'mvn package -DskipTests --no-transfer-progress'
            }
        }

        stage('SonarQube Analysis') {
            when { expression { return env.SONAR_HOST_URL != null } }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar --no-transfer-progress -Dsonar.projectKey=asms'
                }
            }
        }

        stage('ECR Login') {
            when { expression { return env.AWS_ACCOUNT_ID != null } }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'aws-credentials',
                    usernameVariable: 'AWS_ACCESS_KEY_ID',
                    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                )]) {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | \
                        docker login --username AWS --password-stdin ${ECR_REGISTRY}
                    """
                }
            }
        }

        stage('Docker Build & Push') {
            when { expression { return env.AWS_ACCOUNT_ID != null } }
            parallel {
                stage('user-service') {
                    steps { script { buildAndPush('user-service', '.') } }
                }
                stage('support-service') {
                    steps { script { buildAndPush('support-service', '.') } }
                }
                stage('amenity-service') {
                    steps { script { buildAndPush('amenity-service', '.') } }
                }
                stage('visitor-service') {
                    steps { script { buildAndPush('visitor-service', '.') } }
                }
                stage('payment-service') {
                    steps { script { buildAndPush('payment-service', '.') } }
                }
                stage('billing-service') {
                    steps { script { buildAndPush('billing-service', '.') } }
                }
                stage('workflow-service') {
                    steps { script { buildAndPush('workflow-service', '.') } }
                }
                stage('notification-service') {
                    steps { script { buildAndPush('notification-service', '.') } }
                }
                stage('helpbot-service') {
                    steps { script { buildAndPush('helpbot-service', '.') } }
                }
                stage('api-gateway') {
                    steps { script { buildAndPush('api-gateway', '.') } }
                }
                stage('config-server') {
                    steps { script { buildAndPush('config-server', '.') } }
                }
            }
        }

        stage('Deploy to EKS') {
            when { expression { return env.AWS_ACCOUNT_ID != null } }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'aws-credentials',
                    usernameVariable: 'AWS_ACCESS_KEY_ID',
                    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                )]) {
                    sh """
                        aws eks update-kubeconfig \
                            --region ${AWS_REGION} \
                            --name ${EKS_CLUSTER}

                        # Apply namespace, configmap, secrets first
                        kubectl apply -f k8s/base/namespace.yaml
                        kubectl apply -f k8s/base/configmap.yaml
                        kubectl apply -f k8s/base/secrets.yaml

                        # Apply infra pods
                        kubectl apply -f k8s/base/infra-deployments.yaml

                        # Substitute image tags and apply all service deployments
                        for f in k8s/base/*-deployment.yaml; do
                            sed -e 's|\${ECR_REGISTRY}|${ECR_REGISTRY}|g' \
                                -e 's|\${IMAGE_TAG}|${IMAGE_TAG}|g' "\$f" | \
                            kubectl apply -f -
                        done

                        # Apply ingress
                        kubectl apply -f k8s/base/ingress.yaml

                        # Wait for rollout
                        kubectl rollout status deployment/api-gateway -n ${K8S_NAMESPACE} --timeout=300s
                        kubectl rollout status deployment/user-service -n ${K8S_NAMESPACE} --timeout=300s
                    """
                }
            }
        }

        stage('Smoke Test') {
            when { expression { return env.AWS_ACCOUNT_ID != null } }
            steps {
                script {
                    def albDns = sh(
                        script: "kubectl get ingress asms-ingress -n ${K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'",
                        returnStdout: true
                    ).trim()
                    sh """
                        echo "ALB: ${albDns}"
                        curl -sf http://${albDns}/api/v1/users/actuator/health || \
                        curl -sf http://${albDns}/actuator/health
                    """
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline ${IMAGE_TAG} succeeded"
        }
        failure {
            echo "Pipeline ${IMAGE_TAG} FAILED — check logs above"

        }
        always {
            deleteDir()
        }
    }
}

// ─── Helper ────────────────────────────────────────────────────────────────────
def buildAndPush(String service, String rootDir) {
    sh """
        docker build \
            --build-arg BUILDKIT_INLINE_CACHE=1 \
            -t ${ECR_REGISTRY}/asms/${service}:${IMAGE_TAG} \
            -t ${ECR_REGISTRY}/asms/${service}:latest \
            -f ${rootDir}/${service}/Dockerfile \
            ${rootDir}

        docker push ${ECR_REGISTRY}/asms/${service}:${IMAGE_TAG}
        docker push ${ECR_REGISTRY}/asms/${service}:latest
    """
}
