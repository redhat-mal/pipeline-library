def call() {
	env.CHECKOUT_STATUS='false'
	//if (func_not_skip_quality_gate('QG6')) {
		//func_idp_monitor_stage ('Smoke Test - QG6') {
			echo 'Stage: Smoke Test - Start'
			echo 'Stop and remove the existing container (If any exists)'
			try {
				//def pod_stop = 'docker stop ' + SERVICE_NAME
				def ocp_remove = 'oc delete pod ' + SERVICE_NAME + '-smoke'
				sh ocp_remove
			} catch (Exception ex) {
				println("Unable to stop and/or remove the ocp container - " + SERVICE_NAME);
				println(ex.getStackTrace());
			}
			
			try {
				echo 'Run the Docker Image'
				def ocp_run = 'oc run ' + SERVICE_NAME + '-smoke -env PHASE=SMOKE_TEST --image=' + IMAGE_NAME
				sh ocp_run

				echo 'Check the status of the Docker Container. If the status is not running, sleep for a defined interval of 1 sec and check again until 1 min timeout'
				timeout(time: 1, unit: 'MINUTES') {
					def wait_for_ocp = 'until [ "`oc get pods - ' + SERVICE_NAME + '-smoke' --no-headers  |  awk {"print $3"}'`"=="Running" ]; do sleep 1; done;'
					println("Sleeping for 1 sec and wait for the Docker Container - " + SERVICE_NAME + " to start");
					sh wait_for_ocp
				}

				echo 'Identify the Port and the Url of the docker container'
				def inspectCmd = 'docker inspect --format=' + '\'' + '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' + '\' ' + SERVICE_NAME
				APP_PORT =  sh (script: inspectCmd, returnStdout: true).trim()
				println("Docker Container - " + SERVICE_NAME + " is running on Port: " + APP_PORT);
				APP_URL = "http://" + env.NODE_NAME + ":" + APP_PORT
				println("Docker Container - " + SERVICE_NAME + " - Application Url: " + APP_URL);

				echo 'Check if the Spring Boot container has started. If it is not up, sleep for a defined interval of 5 sec and check again until 5 min timeout'
				timeout(time: 5, unit: 'MINUTES') {
					def url = APP_URL + env.SERVLET_CONTEXT_PATH + "/actuator/heartbeat"

					def wait_for_app = 'until $(curl --output /dev/null --silent --head --fail ' + url + '); do sleep 5; done;'
					println("Sleeping for 5 sec and wait for the Spring Boot App to startup - " + url);
					sh wait_for_app
				}
			} catch (Exception ex) {
				def docker_logs = 'docker logs --tail 1000 ' + SERVICE_NAME
				println("Smoke Testing Failed - Docker Logs - ");
				sh docker_logs
				println("Exception while trying to spin up the Docker Container for Smoke Testing.  Exception:");
				ex.printStackTrace();
                if (params.IGNORE_SMOKETEST_FAILURE) {
                     echo "Smoke test failed, but we're ignoring the error."   
                }
                else {
    				error("Aborting the build since the Smoke Testing failed.")
    			}
			}
			
			smoke_testing_script = "curl -s -o /dev/null -w '%{http_code}' --noproxy '*' --request GET --url " + APP_URL +  env.SERVLET_CONTEXT_PATH  + "/actuator/heartbeat"
			
			def HTTP_STATUS = sh (script: smoke_testing_script, returnStdout: true).trim()
			println("Http status code returned from the Health Check Url - " + HTTP_STATUS);

			try {
				if (HTTP_STATUS.trim().equals("200")) {
					println("Smoke Testing completed successfully.  Health Check Http Status Code - " + HTTP_STATUS);
				// if (params.HYGIEIA_PUBLISH_SWITCH.toLowerCase() == 'true') {
          		// 	hygieiaDeployPublishStep applicationName: "${APP_NAME}", artifactDirectory: 'target', artifactGroup: "${NAMESPACE}", artifactName: "${APP_NAME}.jar", artifactVersion: "${VERSION}", buildStatus: 'Success', environmentName: 'QG6'
           		// }
				} else {
				// if (params.HYGIEIA_PUBLISH_SWITCH.toLowerCase() == 'true') {
                 // 	hygieiaDeployPublishStep applicationName: "${APP_NAME}", artifactDirectory: 'target', artifactGroup: "${NAMESPACE}", artifactName: "${APP_NAME}.jar", artifactVersion: "${VERSION}", buildStatus: 'Failure', environmentName: 'QG6'
                //  }
					println("Smoke Testing failed. Health Check Http Status Code - " + HTTP_STATUS);
					if (params.IGNORE_SMOKETEST_FAILURE) {
                        echo "Smoke test failed, but we're ignoring the error."
                    }
                    else {
                        error("Aborting the build since the Smoke Testing failed.")
                    }
				}
			} 
			catch (Exception ex) {
				if (!params.IGNORE_SMOKETEST_FAILURE) {
					throw ex
				}
			}
			finally {
				try {
					echo 'Stop and remove the docker container'
					def docker_stop = 'docker stop ' + SERVICE_NAME
					def docker_remove = 'docker rm ' + SERVICE_NAME
					sh docker_stop
					sh docker_remove
				} catch(Exception ex) {
					println("Unable to stop and/or remove the docker container - " + SERVICE_NAME);
					println(ex.getStackTrace());
				}
			}
			echo 'Stage: Smoke Test - End'
		}
	//}
//}
