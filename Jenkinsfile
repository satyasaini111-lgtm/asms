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
        DOCKER_BUILDKIT = '1'   // enables layer caching
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
                script {
                    // Detect which services changed vs previous commit.
                    // Falls back to rebuilding ALL if no previous commit exists
                    // or if shared files (root pom.xml, k8s/, Jenkinsfile) changed.
                    def changed = sh(
                        script: 'git diff --name-only HEAD~1 HEAD 2>/dev/null || echo "ALL"',
                        returnStdout: true
                    ).trim()

                    def forceAll = (changed == 'ALL') ||
                                   changed.contains('pom.xml') ||
                                   changed.contains('Jenkinsfile')

                    env.CHANGED_FILES = changed
                    env.FORCE_ALL     = forceAll.toString()
                    echo forceAll ? "Full rebuild triggered" : "Changed files:\n${changed}"
                }
            }
        }

        stage('Build JARs') {
            steps {
                // -T 1C uses one Maven thread per CPU core — parallelises module compilation
                sh 'mvn package -DskipTests -T 1C --no-transfer-progress'
            }
        }

        stage('Unit Tests') {
            steps {
                // Run after JARs are built; Maven reuses compiled classes (incremental)
                sh 'mvn test -T 1C --no-transfer-progress -pl user-service,support-service,payment-service,visitor-service'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
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
                    steps { script { buildAndPush('user-service') } }
                }
                stage('support-service') {
                    steps { script { buildAndPush('support-service') } }
                }
                stage('amenity-service') {
                    steps { script { buildAndPush('amenity-service') } }
                }
                stage('visitor-service') {
                    steps { script { buildAndPush('visitor-service') } }
                }
                stage('payment-service') {
                    steps { script { buildAndPush('payment-service') } }
                }
                stage('billing-service') {
                    steps { script { buildAndPush('billing-service') } }
                }
                stage('workflow-service') {
                    steps { script { buildAndPush('workflow-service') } }
                }
                stage('notification-service') {
                    steps { script { buildAndPush('notification-service') } }
                }
                stage('helpbot-service') {
                    steps { script { buildAndPush('helpbot-service') } }
                }
                stage('api-gateway') {
                    steps { script { buildAndPush('api-gateway') } }
                }
                stage('config-server') {
                    steps { script { buildAndPush('config-server') } }
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

                        # Apply namespace, configmap, secrets, and Keycloak realm
                        kubectl apply -f k8s/base/namespace.yaml
                        kubectl apply -f k8s/base/configmap.yaml
                        kubectl apply -f k8s/base/secrets.yaml
                        kubectl apply -f k8s/base/keycloak-realm-configmap.yaml

                        # Apply infra pods
                        sed 's|\${ECR_REGISTRY}|'"${ECR_REGISTRY}"'|g' k8s/base/infra-deployments.yaml | \
                            kubectl apply -f -

                        # Force-delete StatefulSet pods to avoid maxUnavailable:0 deadlock
                        # on EKS Auto Mode (missing pod triggers recreate; unavailable pod doesn't)
                        kubectl delete pod mongodb-0 kafka-0 -n ${K8S_NAMESPACE} --ignore-not-found=true || true

                        kubectl rollout status statefulset/mongodb -n ${K8S_NAMESPACE} --timeout=300s
                        kubectl rollout status statefulset/kafka -n ${K8S_NAMESPACE} --timeout=300s

                        # Apply all service deployments with image substitution
                        for f in k8s/base/*-deployment.yaml; do
                            sed -e 's|\${ECR_REGISTRY}|${ECR_REGISTRY}|g' \
                                -e 's|\${IMAGE_TAG}|${IMAGE_TAG}|g' "\$f" | \
                            kubectl apply -f -
                        done

                        kubectl apply -f k8s/base/ingressclass.yaml
                        kubectl apply -f k8s/base/ingress.yaml

                        # Wait only for gateway + changed services (others roll out in background)
                        kubectl rollout status deployment/api-gateway -n ${K8S_NAMESPACE} --timeout=600s &
                        PIDS="\$!"

                        CHANGED="${env.CHANGED_FILES}"
                        FORCE="${env.FORCE_ALL}"
                        for svc in user-service amenity-service support-service visitor-service \
                                   payment-service billing-service workflow-service \
                                   notification-service helpbot-service; do
                            if [ "\$FORCE" = "true" ] || echo "\$CHANGED" | grep -q "^\$svc/"; then
                                kubectl rollout status deployment/\$svc -n ${K8S_NAMESPACE} --timeout=600s &
                                PIDS="\$PIDS \$!"
                            fi
                        done

                        wait \$PIDS
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

                        # Poll until ALB hostname appears (up to 5 min)
                        albDns=''
                        for i in \$(seq 1 20); do
                            albDns=\$(kubectl get ingress asms-ingress -n ${K8S_NAMESPACE} \
                                -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
                            [ -n "\$albDns" ] && break
                            echo "Waiting for ALB... attempt \$i/20"
                            sleep 15
                        done

                        [ -z "\$albDns" ] && echo "ERROR: ALB not provisioned" && exit 1

                        # Poll ALB health instead of fixed sleep
                        echo "Polling ALB at http://\$albDns ..."
                        for i in \$(seq 1 20); do
                            STATUS=\$(curl -s -o /dev/null -w "%{http_code}" \
                                --connect-timeout 5 "http://\$albDns/actuator/health" 2>/dev/null || echo "000")
                            [ "\$STATUS" = "200" ] && echo "ALB healthy (HTTP \$STATUS)" && break
                            echo "ALB not ready yet (HTTP \$STATUS), attempt \$i/20..."
                            sleep 15
                        done

                        curl -sf --retry 3 --retry-delay 10 \
                            "http://\$albDns/actuator/health"
                    """
                }
            }
        }
    }

    post {
        success { echo "Pipeline ${IMAGE_TAG} succeeded" }
        failure { echo "Pipeline ${IMAGE_TAG} FAILED — check logs above" }
        always  { deleteDir() }
    }
}

// ─── Helper ────────────────────────────────────────────────────────────────────
def buildAndPush(String service) {
    def changed = env.CHANGED_FILES ?: 'ALL'
    def forceAll = env.FORCE_ALL == 'true'

    if (!forceAll && !changed.contains("${service}/")) {
        echo "Skipping ${service} — no source changes detected; re-tagging latest"
        sh """
            # Re-tag existing latest image with new build tag so deployment YAML resolves
            MANIFEST=\$(aws ecr batch-get-image \
                --repository-name asms/${service} \
                --image-ids imageTag=latest \
                --region ${AWS_REGION} \
                --query 'images[0].imageManifest' --output text 2>/dev/null || true)
            if [ -n "\$MANIFEST" ] && [ "\$MANIFEST" != "None" ]; then
                aws ecr put-image \
                    --repository-name asms/${service} \
                    --image-tag ${IMAGE_TAG} \
                    --image-manifest "\$MANIFEST" \
                    --region ${AWS_REGION} 2>/dev/null || true
                echo "Re-tagged asms/${service}:latest → ${IMAGE_TAG}"
            else
                echo "No existing image found for ${service}, forcing full build"
                _doBuild('${service}')
            fi
        """
        return
    }

    _doBuild(service)
}

def _doBuild(String service) {
    sh """
        # Pull latest for layer cache (best-effort)
        docker pull ${ECR_REGISTRY}/asms/${service}:latest 2>/dev/null || true

        docker build \
            --cache-from ${ECR_REGISTRY}/asms/${service}:latest \
            -t ${ECR_REGISTRY}/asms/${service}:${IMAGE_TAG} \
            -t ${ECR_REGISTRY}/asms/${service}:latest \
            -f ${service}/Dockerfile \
            .

        docker push ${ECR_REGISTRY}/asms/${service}:${IMAGE_TAG}
        docker push ${ECR_REGISTRY}/asms/${service}:latest
    """
}
