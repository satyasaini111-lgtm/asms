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

        stage('Mirror Infra Images') {
            when { expression { return env.AWS_ACCOUNT_ID != null } }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'aws-credentials',
                    usernameVariable: 'AWS_ACCESS_KEY_ID',
                    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                )]) {
                    sh """
                        # Create ECR repos for third-party infra images (idempotent)
                        for repo in asms/mongodb asms/redis asms/kafka asms/keycloak asms/busybox; do
                            aws ecr create-repository --repository-name \$repo \
                                --region ${AWS_REGION} 2>/dev/null || true
                        done

                        mirror_image() {
                            local src="\$1" repo="\$2" tag="\$3"
                            aws ecr describe-images --repository-name "\$repo" \
                                --image-ids imageTag="\$tag" \
                                --region ${AWS_REGION} >/dev/null 2>&1 && return 0
                            docker pull "\$src"
                            docker tag "\$src" "${ECR_REGISTRY}/\$repo:\$tag"
                            docker push "${ECR_REGISTRY}/\$repo:\$tag"
                        }

                        mirror_image mongo:7.0                         asms/mongodb  7.0
                        mirror_image redis:7.2-alpine                  asms/redis    7.2-alpine
                        mirror_image confluentinc/cp-kafka:7.6.1       asms/kafka    7.6.1
                        mirror_image quay.io/keycloak/keycloak:24.0.5  asms/keycloak 24.0.5
                        mirror_image busybox:1.36                      asms/busybox  1.36
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

                        # Apply infra pods (substitute ECR_REGISTRY placeholder)
                        sed 's|\${ECR_REGISTRY}|'"${ECR_REGISTRY}"'|g' k8s/base/infra-deployments.yaml | \
                            kubectl apply -f -

                        # Wait for MongoDB and Kafka to be ready before deploying services
                        # (domain service initContainers depend on these being reachable)
                        kubectl rollout status statefulset/mongodb -n ${K8S_NAMESPACE} --timeout=600s
                        kubectl rollout status statefulset/kafka -n ${K8S_NAMESPACE} --timeout=300s

                        # Substitute image tags and apply all service deployments
                        for f in k8s/base/*-deployment.yaml; do
                            sed -e 's|\${ECR_REGISTRY}|${ECR_REGISTRY}|g' \
                                -e 's|\${IMAGE_TAG}|${IMAGE_TAG}|g' "\$f" | \
                            kubectl apply -f -
                        done

                        # Apply ingress
                        kubectl apply -f k8s/base/ingress.yaml

                        # Wait for rollout
                        kubectl rollout status deployment/api-gateway -n ${K8S_NAMESPACE} --timeout=600s
                        kubectl rollout status deployment/user-service -n ${K8S_NAMESPACE} --timeout=600s
                    """
                }
            }
        }

        stage('Smoke Test') {
            when { expression { return env.AWS_ACCOUNT_ID != null } }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'aws-credentials',
                    usernameVariable: 'AWS_ACCESS_KEY_ID',
                    passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                )]) {
                    sh """
                        aws eks update-kubeconfig --region ${AWS_REGION} --name ${EKS_CLUSTER}

                        # Wait up to 5 min for ALB to be provisioned by the AWS Load Balancer Controller
                        albDns=''
                        for i in \$(seq 1 20); do
                            albDns=\$(kubectl get ingress asms-ingress -n ${K8S_NAMESPACE} \
                                -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
                            if [ -n "\$albDns" ]; then
                                echo "ALB provisioned: \$albDns"
                                break
                            fi
                            echo "Waiting for ALB hostname... attempt \$i/20"
                            sleep 15
                        done

                        if [ -z "\$albDns" ]; then
                            echo "ERROR: ALB not provisioned after 5 minutes"
                            exit 1
                        fi

                        # Give ALB health checks 60s to pass before hitting it
                        sleep 60

                        curl -sf --retry 6 --retry-delay 15 --retry-connrefused \
                            "http://\$albDns/api/v1/users/actuator/health" || \
                        curl -sf --retry 6 --retry-delay 15 --retry-connrefused \
                            "http://\$albDns/actuator/health"
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
