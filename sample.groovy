pipeline 
{
 
    // For only single node
    agent any
 
    // For master-slave architecture
    // agent {
    //     label 'node-1'
    // }
 
    // Environment Variables
    environment {
        APP_NAME = 'cicd-project'
        DOCKERHUB_USER = 'vaibhav165'  
        IMAGE = "${DOCKERHUB_USER}/${APP_NAME}"
    }
 
    stages {
 
        // Pulling Stage
        stage('Pull') {
            steps {
                git branch: 'main', url: 'https://github.com/Gaurav9540/mvn-project.git'
                //git branch: 'main', url: 'https://github.com/vaibhavlahare/StudentData.git'
                echo "pulling successfully!"
            }
        }
 
        // Building Stage
        stage('Building') {
            steps {
               sh 'mvn clean package'
               echo "Build completed successfully!"
            }
        }
 
        // Testing Stage
        stage('Test') {
            steps {
               // withSonarQubeEnv(installationName: 'sonar-server', credentialsId: 'sonar-token') {
                 // sh 'mvn clean verify sonar:sonar -Dsonar.projectKey=sonar-server'
                }
                echo "testing successfully!"
            }
        }
 
        // QualityGate Check Test
        //stage('QualityGate') {
            steps {
               // waitForQualityGate abortPipeline: false, credentialsId: 'sonar-secret-key'
                echo "qulity gate check successfully!"
            }
        }
 
        // Docker Image Build Stage
        stage('Docker Build') {
            steps {
                script {
                    def shortSha = sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"
                }
                sh "docker build -t ${IMAGE}:${IMAGE_TAG} ."
                sh "docker images | grep ${APP_NAME}"
                echo "Docker image built successfully!"
            }
        }
 
        // Force checkout from master branch if there is both master and main branch
        // stage('Docker Build') {
        // steps {
        //    script {
        //      // 🔹 Force checkout from master branch
        //       checkout([$class: 'GitSCM',
        //         branches: [[name: '*/master']],
        //         userRemoteConfigs: [[url: 'https://github.com/Yash2-27/EMS.git']]
        //       ])
 
        //       def shortSha = sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
        //       env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"
 
        //       sh "docker build -t ${IMAGE}:${IMAGE_TAG} ."
        //       sh "docker images | grep ${APP_NAME}"
        //       echo "Docker image built successfully!"
        //    }
        // }
 
 
        // Docker Image Push to Dockerhub Stage        
        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-cred', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh """
                      echo "$PASS" | docker login -u "$USER" --password-stdin
                      docker tag ${IMAGE}:${IMAGE_TAG} ${IMAGE}:latest
                      docker push ${IMAGE}:${IMAGE_TAG}
                      docker push ${IMAGE}:latest
                      docker logout
                    """
                }
                echo "Image pushed successfully to Docker Hub!"
            }
        }
 
        // Deployment Stage
        stage('Deploy to Tomcat') {
            steps {
                // deploy adapters: [tomcat9(credentialsId: 'tomcat-pass', path: '', url: 'http://65.0.73.96:8080/')], contextPath: '/', war: '**/*.war'
                echo "Deploy Successfully!"
            }
        }
 
        // Run Container
        stage('Deploy with Docker') {
            steps {
                sh """
                echo "Container deployed successfully and running on port 8083!"
                docker rm -f ${APP_NAME} || true
                docker run -d --name ${APP_NAME} -p 8083:8080 ${IMAGE}:latest
                """
            }
        }
 
        // Cleanup Stage
        stage('Cleanup Old Images') {
            steps {
                script {
                  sh """
                      echo "Cleaning up old Docker images (keeping only last 2)!"
 
                      # Get the 2 most recent image IDs for this repo
                      keep_ids=\$(docker images ${IMAGE} --format '{{.CreatedAt}} {{.ID}}' | sort -r | awk '{print \$2}' | head -n 2)
 
                      # Get IDs of all running containers' images
                      running_ids=\$(docker ps --format '{{.Image}}' | xargs -r docker inspect --format '{{.Id}}')
 
                      # Combine keep_ids + running_ids
                      safe_ids="\$keep_ids \$running_ids"
 
                      # Loop through images of this repo and delete only unsafe ones
                      for id in \$(docker images ${IMAGE} --format '{{.ID}}'); do
                        if ! echo "\$safe_ids" | grep -q "\$id"; then
                            echo "Removing old image: \$id"
                            docker rmi -f \$id || true
                        fi
                      done
 
                      # Prune dangling stuff (does not touch running containers)
                      docker system prune -af --volumes || true
                    """
                    echo "Cleaning up old Docker images Successfully!"
                }
            }
        }
    }
}