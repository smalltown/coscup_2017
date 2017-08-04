import jenkins.model.Jenkins

folder('dc')
folder('marvel')

// approve the pipeline groovy automaitcally
def scriptApproval = Jenkins.instance.getExtensionList('org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval')[0]

scriptApproval.approveSignature("new java.io.File java.lang.String")
scriptApproval.approveSignature("method java.io.File exists")
scriptApproval.approveSignature("method java.io.File isDirectory")
