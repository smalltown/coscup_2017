import jenkins.model.Jenkins

// define global variable
def job_folder = 'Marvel'
def job_name = 'Iron_Man'

def choice_groovy = 'File f = new File(\'/var/lib/jenkins/version/' + job_folder + '/' + job_name + '/version\')\n\nif(f.exists() && !f.isDirectory()) {\n  String fileContents = new File(\'/var/lib/jenkins/version/' + job_folder + '/' + job_name + '/version\').text\n  def list = Eval.me(fileContents)\n  return list\n}\nelse {\n  return [\'none\']\n}'

// load config file
def config = new ConfigSlurper().parse(readFileFromWorkspace('config/common.groovy'))

pipelineJob("${job_folder}/${job_name}") {

  properties {
    buildDiscarderProperty {
      strategy {
        logRotator {
          artifactDaysToKeepStr('')
          artifactNumToKeepStr('')
          daysToKeepStr('')
          numToKeepStr(config.numToKeepStr)
        }
      }
    }

    parametersDefinitionProperty {
      parameterDefinitions {
        choiceParameterDefinition {
          name('phase')
          choices (config.phase_all)
          description(config.phase_description)
        }

        extensibleChoiceParameterDefinition {
          name('target_branch')
          description(config.gitBranch_description)
          editable(true)

          choiceListProvider {
            textareaChoiceListProvider {
              choiceListText('master\ndevelop')
              defaultChoice('')
              addEditedValue(false)
              whenToAdd('Triggered')
            }
          }
        }

        extensibleChoiceParameterDefinition {
          name('version')
          description(config.version_description)
          editable(true)
  
          choiceListProvider {
            systemGroovyChoiceListProvider {
              groovyScript {
                script(choice_groovy)
                sandbox(true)
              }
              defaultChoice('none')
              usePredefinedVariables(false)
            }
          }
        }
      }
    }
  }

  definition {
    cps {
      script(readFileFromWorkspace("pipeline/${job_folder}/${job_name}.groovy"))
    }
  }
}

// approve the pipeline groovy automaitcally
//def groovyscript = readFileFromWorkspace("pipeline/${job_folder}/${job_name}.groovy")
//def scriptApproval = Jenkins.instance.getExtensionList('org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval')[0]
//scriptApproval.approveScript(scriptApproval.hash(groovyscript, 'groovy'))

