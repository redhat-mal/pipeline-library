def call() {
	env.CHECKOUT_STATUS='false'
        env.SERVLET_CONTEXT_PATH2='/'
	//if (func_not_skip_quality_gate('QG6')) {
		//func_idp_monitor_stage ('Smoke Test - QG6') {
			echo 'Stage: Smoke Test - Start'
			echo 'Stop and remove the existing container (If any exists)'
			try {
				//def pod_stop = 'docker stop ' + SERVICE_NAME
				def ocp_remove = 'oc delete pod ' + SERVICE_NAME + '-smoke' 
				sh ocp_remove
                                def ocp_svc_remove = 'oc delete svc ' + SERVICE_NAME + '-smoke' 
                                sh ocp_svc_remove

			} catch (Exception ex) {
				println("Unable to stop and/or remove the ocp container - " + SERVICE_NAME);
				println(ex.getStackTrace());
			}
			
			try {
				echo 'Run the Docker Image'
				def ocp_run = 'oc run ' + SERVICE_NAME + '-smoke --env PHASE=SMOKE_TEST --image=' + IMAGE_NAME
				sh ocp_run + ' --labels=app=' + SERVICE_NAME + '-smoke --restart=Never' 

				echo 'Check the status of the Docker Container. If the status is not running, sleep for a defined interval of 1 sec and check again until 1 min timeout'
				timeout(time: 1, unit: 'MINUTES') {
                                        def wait_for_ocp = 'while [ "`oc get pods ' + SERVICE_NAME + '-smoke --no-headers |  awk \'{print $3}\'`" != "Running" ]; do echo $i; done;';

					//def wait_for_ocp = 'until [ "`oc get pods ' + SERVICE_NAME + '-smoke -n ' + DEV_PROJECT + ' --no-headers  |  awk {"print $3"}`"=="Running" ]; do sleep 1; done;'
					println("Sleeping for 1 sec and wait for the Docker Container - " + SERVICE_NAME + " to start");
                                        println("CMD:" + wait_for_ocp);
					sh wait_for_ocp
				}

				echo 'Identify the Port and the Url of the docker container'
				//def inspectCmd = 'docker inspect --format=' + '\'' + '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' + '\' ' + SERVICE_NAME
				APP_PORT =  "8080"
                                def svcCmd = 'oc create svc clusterip ' + SERVICE_NAME + '-smoke --tcp=8080:8080'
                                sh svcCmd
				APP_URL = "http://" + SERVICE_NAME + "-smoke:" + APP_PORT
				println("OCP Container - " + SERVICE_NAME + " - Application Url: " + APP_URL);

				echo 'Check if the Spring Boot container has started. If it is not up, sleep for a defined interval of 5 sec and check again until 5 min timeout'
				timeout(time: 5, unit: 'MINUTES') {
                                        sh "env"
                                        println("Check APP" + env.SERVLET_CONTEXT_PATH2);
					def url = APP_URL + env.SERVLET_CONTEXT_PATH2  +  "/actuator/heartbeat"
                                        println("URL:" + url);
					def wait_for_app = 'until $(curl --output /dev/null --silent --head --fail ' + url + '); do sleep 5; done;'
                                        println("wait:" + wait_for_app);
					println("Sleeping for 5 sec and wait for the Spring Boot App to startup - " + url);
					sh wait_for_app
				}
			} catch (Exception ex) {
				def ocp_logs = 'oc logs ' + SERVICE_NAME + '-smoke'
				println("Smoke Testing Failed - Docker Logs - ");
				sh ocp_logs
				println("Exception while trying to spin up the OCP Container for Smoke Testing.  Exception:");
				ex.printStackTrace();
                if (params.IGNORE_SMOKETEST_FAILURE) {
                     echo "Smoke test failed, but we're ignoring the error."   
                }
                else {
    				error("Aborting the build since the Smoke Testing failed.")
    			}
			}
			
			smoke_testing_script = "curl -s -o /dev/null -w '%{http_code}' --noproxy '*' --request GET --url " + APP_URL +  env.SERVLET_CONTEXT_PATH2  + "/actuator/heartbeat"
			
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
					echo 'Stop and remove the ocp container'
					def pod_remove = 'ocp delete pod  ' + SERVICE_NAME + '-smoke'
                                        def svc_remove = 'ocp delete svc  ' + SERVICE_NAME + '-smoke'
					sh pod_remove
					sh svc_remove
				} catch(Exception ex) {
					println("Unable to stop and/or remove the ocp container - " + SERVICE_NAME);
					println(ex.getStackTrace());
				}
			}
			echo 'Stage: Smoke Test - End'
		}
	//}
//}
