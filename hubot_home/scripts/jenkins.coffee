# Description:
#   Interact with your Jenkins CI server
#
# Dependencies:
#   None
#
# Configuration:
#   HUBOT_JENKINS_URL
#   HUBOT_JENKINS_AUTH
#
#   Auth should be in the "user:password" format.
#
# Commands:
#   hubot jenkins b <jobNumber> - builds the job specified by jobNumber. List jobs to get number.
#   hubot jenkins build <job> - builds the specified Jenkins job
#   hubot jenkins build <job>, <params> - builds the specified Jenkins job with parameters as key=value&key2=value2
#   hubot jenkins list <filter> - lists Jenkins jobs
#   hubot jenkins describe <job> - Describes the specified Jenkins job
#   hubot jenkins last <job> - Details about the last build for the specified Jenkins job

#
# Author:
#   dougcole

querystring = require 'querystring'

# Holds a list of jobs, so we can trigger them with a number
# instead of the job's name. Gets populated on when calling
# list.
jobList = []

jenkinsBuildById = (msg) ->
  # Switch the index with the job name
  job = jobList[parseInt(msg.match[1]) - 1]

  if job
    msg.match[1] = job
    jenkinsBuild(msg)
  else
    msg.reply "I couldn't find that job. Try `jenkins list` to get a list."

jenkinsBuild = (msg, buildWithEmptyParameters) ->
    url = process.env.HUBOT_JENKINS_URL
    job = msg.match[1].replace /\//g, "/job/"
    job = querystring.escape job
    job = job.replace /%2F/g, "/"

    msg.send "The job is #{job}"

    params = msg.match[3]
    command = if buildWithEmptyParameters then "buildWithParameters" else "build"
    path = if params then "#{url}/job/#{job}/buildWithParameters?#{params}" else "#{url}/job/#{job}/#{command}"

    msg.send "The path is #{path}"


    req = msg.http(path)

    if process.env.HUBOT_JENKINS_AUTH
      auth = new Buffer(process.env.HUBOT_JENKINS_AUTH).toString('base64')
      req.headers Authorization: "Basic #{auth}"

    req.header('Content-Length', 0)
    req.post() (err, res, body) ->
        if err
          msg.reply "Jenkins says: #{err}"
        else if 200 <= res.statusCode < 400 # Or, not an error code.
          msg.reply "(#{res.statusCode}) Build started for #{job} #{url}/job/#{job}"
        else if 400 == res.statusCode
          jenkinsBuild(msg, true)
        else if 404 == res.statusCode
          msg.reply "Build not found, double check that it exists and is spelt correctly."
        else
          msg.reply "Jenkins says: Status #{res.statusCode} #{body}"


jenkinsList = (msg, jobFolder = "") ->
  url = process.env.HUBOT_JENKINS_URL
  filter = new RegExp(msg.match[2], 'i')

  jobURI = jobFolder.replace /\//g, "/job/"

  jobURI = if jobURI == ""
             ""
           else
             "/job/#{jobURI}"

  req = msg.http("#{url}#{jobURI}/api/json")

  if process.env.HUBOT_JENKINS_AUTH
    auth = new Buffer(process.env.HUBOT_JENKINS_AUTH).toString('base64')
    req.headers Authorization: "Basic #{auth}"

  req.get() (err, res, body) ->
    response = ""

    if err
      msg.send "Jenkins says: #{err}"
    else
      try
        content = JSON.parse(body)
        for job in content.jobs
          # Add the job to the jobList
          index = jobList.indexOf(job.name)

          isJobFolder = not job.color?

          jobPath = if jobFolder == ""
                      job.name
                    else
                      "#{jobFolder}/#{job.name}"

          if msg.match[2]? and (filter.test job.name) and isJobFolder
            jenkinsList msg, jobPath
          else
            if index == -1
              jobList.push(jobPath)
              index = jobList.indexOf(jobPath)

            state = if job.color == "red"
                      "FAIL"
                    else if job.color == "aborted"
                      "ABORTED"
                    else if job.color == "aborted_anime"
                      "CURRENTLY RUNNING"
                    else if job.color == "red_anime"
                      "CURRENTLY RUNNING"
                    else if job.color == "blue_anime"
                      "CURRENTLY RUNNING"
                    else "PASS"

            if (filter.test job.name) or (filter.test jobFolder) or (filter.test state)
              if isJobFolder
                response += "[#{index + 1}] #{jobPath}/ : Folder\n"
              else
                response += "[#{index + 1}] #{jobPath} : #{state}\n"

        msg.send response
      catch error
        msg.send error

module.exports = (robot) ->

  robot.respond /j(?:enkins)? build ([\w\.\-_\/ ]+)(, (.+))?/i, (msg) ->
    jenkinsBuild msg, false

  robot.respond /j(?:enkins)? b (\d+)/i, (msg) ->
    jenkinsBuildById msg

  robot.respond /j(?:enkins)? list( (.+))?/i, (msg) ->
    jenkinsList msg

  robot.respond /daily report/i, (msg) ->
    msg.match[1] = 'report/magento'
    jenkinsBuild msg, false

