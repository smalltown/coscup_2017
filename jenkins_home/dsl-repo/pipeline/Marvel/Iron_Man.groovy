import groovy.json.*

// load share variable
def build_info = build_info.load()

// define pipeline global variable
def job_info = [:]

job_info['folder'] = 'Marvel'
job_info['name'] = env.JOB_BASE_NAME
job_info['repo_host'] = build_info['gitlab']['host']
job_info['repo_owner'] = build_info['gitlab']['owner']
job_info['repo_name'] = 'agora_lemp'
job_info['repo_branch'] = (env.gitlabSourceBranch == null) ? target_branch : env.gitlabSourceBranch
job_info['trigger_user'] = ''
job_info['version_info'] = [:]
job_info['ami'] = build_info['ami']
job_info['k8s'] = [:]
job_info['k8s']['headless'] = 'false'
job_info['k8s']['autoscaling'] = 'false'

@NonCPS
def parseJsonText(String jsonText) {
  final slurper = new JsonSlurper()
  return new HashMap<>(slurper.parseText(jsonText))
}

pipeline {

  agent none

  stages {
    stage('Build') {
      agent { label 'Build' }
      when { expression { version == 'none' } }

      steps {
        script { 
          def workspace = pwd()
          job_info['trigger_user'] = triggerUser(requester)
        }

        deleteDir()
        
        // retrieve code from git
        checkout([$class: 'GitSCM', branches: [[name: "remotes/origin/${job_info['repo_branch']}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${job_info['folder']}_${job_info['name']}"], [$class: 'LocalBranch', localBranch: job_info['repo_branch']]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: build_info['gitlab']['ssh_credentialsId'], url: "git@${job_info['repo_host']}:${job_info['repo_owner']}/${job_info['repo_name']}.git"]]])
        
        // bump new version number
        dir("./${job_info['folder']}_${job_info['name']}") {
          script {
            def latest_tag = sh(script: "git tag -l --sort=-v:refname | grep \'v[0-9]\\+\\.[0-9]\\+\\.[0-9]\\+_\' | head -1", returnStdout: true)
            job_info['version_info']['build_number'] = sh(script: "python ${env.JENKINS_HOME}/workflow-libs/helper/version/manage.py ${versionPattern}  ${latest_tag}", returnStdout: true)

            def flat_repo_branch = job_info['repo_branch'].replaceAll('/','_')

            job_info['version_info']['build_number'] = job_info['version_info']['build_number'].trim() + "_${flat_repo_branch}_${job_info['trigger_user']}"
          }
        }

         // build docker images
        dir("${env.JENKINS_HOME}/packer-repo") {
          sh "packer build -var-file=variables/common/global.json -var-file=variables/${job_info['folder']}/${job_info['name']}.json -var 'region=${build_info['aws_regions']['origin_region']}' -var 'app_tag=${job_info['version_info']['build_number']}' -var 'workspace=${workspace}' templates/${job_info['folder']}/${job_info['name']}.json"
        }

        // push build_number to gitlab
        dir("./${job_info['folder']}_${job_info['name']}") {
          withEnv(["GIT_SSH=${env.JENKINS_HOME}/.ssh/ssh-git.sh", "JENKINS_HOME=${env.JENKINS_HOME}", "BRANCH=${job_info['repo_branch']}", "TAG=${job_info['version_info']['build_number']}"]) {
            sh '''
              # push tag to remote git service
              git config --global user.email jarvis@htc.com
              git config --global user.name jarvis
              git tag -a ${TAG} -m ${TAG}
              PKEY=${JENKINS_HOME}/.ssh/jarvis git push origin ${TAG}
            '''
          }

          sh "echo 'none' > ../${job_info['repo_owner']}_${job_info['repo_name']}.git.tag"
          sh "git tag -l --sort=-v:refname | grep \'v[0-9]\\+\\.[0-9]\\+\\.[0-9]\\+_\' | head -15 >> ../${job_info['repo_owner']}_${job_info['repo_name']}.git.tag"

          script {
            def version_list = readFile("${workspace}/${job_info['repo_owner']}_${job_info['repo_name']}.git.tag")
            job_info['version_info']['version_list'] = version_list.split()
          }
        }
      }
    }
    stage('Deploy') {
      agent { label 'Build' }
      when { expression { version != 'none' } }

      steps {
        script {
          def workspace = pwd()
          job_info['trigger_user'] = triggerUser()
        }

        dir("${env.JENKINS_HOME}/ansible-repo") {
          sh "ansible-playbook -i \"localhost,\" -c local --extra-vars \"region=us-west-2 phase=${phase} deploy_project=${job_info['folder']} deploy_module=${job_info['name']} headless=${job_info['k8s']['headless']} autoscaling=${job_info['k8s']['autoscaling']} app_tag=${version}\" playbooks/common/deploy_kubernetes.yml"
        }
      }
    }

    stage('Save_Build_Number') {
      agent { label 'Master' }
      when { expression { version == 'none' } }

      steps {
        sh "mkdir -p ${env.JENKINS_HOME}/version/${job_info['folder']}/${job_info['name']}"
        script {
          def version_list_string = '[\\\'' + job_info['version_info']['version_list'].join('\\\', \\\'') + '\\\']'
          sh "echo ${version_list_string} > ${env.JENKINS_HOME}/version/${job_info['folder']}/${job_info['name']}/version"
        }
      }
    }
  }

  post {
    success {
      script {
        if ( version != 'none' ) {
          deployNotify(phase, version, job_info['trigger_user'], job_info['repo_branch'], 'Success', job_info['region'], job_info['repo_name'])
        }
        else {
          buildNotify(job_info['version_info']['build_number'], job_info['trigger_user'])
        }
      }
    }

    failure {
      script {
        if ( version != 'none' ) {
           deployNotify(phase, version, job_info['trigger_user'], job_info['repo_branch'], 'Fail', job_info['region'], job_info['repo_name'])
        }
        else {
          buildNotify(job_info['version_info']['build_number'], job_info['trigger_user'], 'Fail')
        }
      }
    }
  }
}

