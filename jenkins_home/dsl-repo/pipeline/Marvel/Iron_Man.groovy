@Library('pipeline-lib') _

// load share variable
def build_info = build_info.load()

// define pipeline global variable
def job_info = [:]

job_info['folder'] = 'marvel'
job_info['name'] = env.JOB_BASE_NAME
job_info['repo_host'] = build_info['github']['host']
job_info['repo_owner'] = build_info['github']['owner']
job_info['repo_name'] = 'coscup_2017_sample_app'
job_info['version_info'] = [:]
job_info['version_info']['app_tag_prefix'] = 'iron_man'
job_info['trigger_user'] = ''
job_info['workspace'] = ''
job_info['k8s'] = [:]
job_info['k8s']['headless'] = 'false'
job_info['k8s']['autoscaling'] = 'false'

pipeline {

  agent any

  stages {
    stage('Build') {
      when { expression { version == 'none' } }

      steps {
        script { 
          job_info['workspace'] = pwd()
          job_info['trigger_user'] = triggerUser()
          app_tag = new Date().format( 'yyyyMMddHHmmss' )
        }

        deleteDir()
        
        // retrieve code from git
        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${job_info['folder']}_${job_info['name']}"]], submoduleCfg: [], userRemoteConfigs: [[url: "https://${job_info['repo_host']}/${job_info['repo_owner']}/${job_info['repo_name']}.git"]]])

         // build docker images
        dir("${env.JENKINS_HOME}/packer-repo") {
          sh """
            sudo -E bash -c \"PACKER_TMP_DIR=/tmp packer build -var-file=variables/${job_info['folder']}/${job_info['name']}.json -var 'job_folder=${job_info['folder']}' -var 'job_name=${job_info['name']}' -var 'app_tag=${job_info['version_info']['app_tag_prefix']}_${app_tag}' -var 'workspace=${job_info['workspace']}' templates/${job_info['folder']}/${job_info['name']}.json\"
          """
        }
      }
    }

    stage('Deploy') {
      when { expression { version != 'none' } }

      steps {
        script {
          job_info['workspace'] = pwd()
          job_info['trigger_user'] = triggerUser()
        }

        dir("${env.JENKINS_HOME}/ansible-repo") {
          echo 'later'
          //sh "ansible-playbook -i \"localhost,\" -c local --extra-vars \"region=us-west-2 phase=${phase} deploy_project=${job_info['folder']} deploy_module=${job_info['name']} headless=${job_info['k8s']['headless']} autoscaling=${job_info['k8s']['autoscaling']} app_tag=${version}\" playbooks/common/deploy_kubernetes.yml"
        }
      }
    }

    //stage('Save_Build_Number') {
    //  when { expression { version == 'none' } }

    //  steps {
    //    sh "mkdir -p ${env.JENKINS_HOME}/version/${job_info['folder']}/${job_info['name']}"
    //    script {
    //      def version_list_string = '[\\\'' + job_info['version_info']['version_list'].join('\\\', \\\'') + '\\\']'
    //      sh "echo ${version_list_string} > ${env.JENKINS_HOME}/version/${job_info['folder']}/${job_info['name']}/version"
    //    }
    //  }
    //}
  }

  post {
    success {
      script {
        if ( version != 'none' ) {
          //deployNotify(phase, version, job_info['trigger_user'], job_info['repo_branch'], 'Success', job_info['region'], job_info['repo_name'])
        }
        else {
          //buildNotify(job_info['version_info']['build_number'], job_info['trigger_user'])
        }
      }
    }

    failure {
      script {
        if ( version != 'none' ) {
           //deployNotify(phase, version, job_info['trigger_user'], job_info['repo_branch'], 'Fail', job_info['region'], job_info['repo_name'])
        }
        else {
          //buildNotify(job_info['version_info']['build_number'], job_info['trigger_user'], 'Fail')
        }
      }
    }
  }
}

