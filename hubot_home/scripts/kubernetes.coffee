# Description:
#   Hubot Kubernetes REST API helper commands.
#
# Dependencies:
#   None
#
# Configuration:
#   KUBE_HOST
#   KUBE_VERSION
#   KUBE_CONTEXT
#   KUBE_CA
#   KUBE_TOKENS
#
# Commands:
#   hubot k8s [po|rc|svc] (labels) - List all k8s resources under given context
#   hubot k8s context <name> - Show/change current k8s context
#
# Author:
#   canthefason

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0"

module.exports = (robot) ->

  getContext = (res) ->
    user = res.message.user.id
    key = "#{user}.context"

    return robot.brain.get(key) or defaultCtx

  setContext = (res, context) ->
    user = res.message.user.id
    key = "#{user}.context"

    return robot.brain.set(key, context or defaultCtx)

  defaultCtx = "default" or process.env.KUBE_CONTEXT

  kubeapi = new Request()

  aliasMap =
    "svc": "services"
    "rc": "replicationcontrollers"
    "po": "pods"

  decorateFnMap =
    'replicationcontrollers': (response) ->
      reply = ''
      for rc in response.items
        image = rc.spec.template.spec.containers[0].image
        {metadata: {name, creationTimestamp}, spec: {replicas}} = rc
        reply += ">*#{name}*: \n"+
        ">Replicas: #{replicas}\n>Age: #{timeSince(creationTimestamp)}\n"+
        ">Image: #{image}\n"

      return reply
    'services': (response) ->
      reply = ''
      for service in response.items
        {metadata: {creationTimestamp}, spec: {clusterIP, ports}} = service
        ps = ""
        for p in ports
          {protocol, port} = p
          ps += "#{port}/#{protocol} "
        reply += ">*#{service.metadata.name}*:\n" +
        ">Cluster ip: #{clusterIP}\n>Ports: #{ps}\n>Age: #{timeSince(creationTimestamp)}\n"
      return reply
    'pods': (response) ->
      reply = ''
      for pod in response.items
        {metadata: {name}, status: {phase, startTime, containerStatuses}} = pod
        reply += ">*#{name}*: \n>Status: #{phase} for: #{timeSince(startTime)} \n"
        for cs in containerStatuses
          {name, restartCount, image} = cs
          reply += ">Name: #{name} \n>Restarts: #{restartCount}\n>Image: #{image}\n"

      return reply


  robot.respond /k8s\s*(services|pods|replicationcontrollers|svc|po|rc)\s*(.+)?/i, (res) ->
    namespace = getContext(res)
    type = res.match[1]

    if alias = aliasMap[type] then type = alias

    url = "namespaces/#{namespace}/#{type}"

    if res.match[2] and res.match[2] != ""
      url += "?labelSelector=#{res.match[2].trim()}"

    #roles = robot.auth.userRoles res.envelope.user
    kubeapi.get {path: url}, (err, response) ->
      if err
        robot.logger.error err
        return res.send "Could not fetch #{type} on *#{namespace}*"

      return res.reply 'Requested resource is not found'  unless response.items and response.items.length

      reply = "\n"
      decorateFn = decorateFnMap[type] or ->
      reply = "Here is the list of #{type} running on *#{namespace}*\n"
      reply += decorateFn response

      res.reply reply


  # update/fetch kubernetes context
  robot.respond /k8s\s*context\s*(.+)?/i, (res) ->
    context = res.match[1]
    if not context or context is ""
      return res.reply "Your current context is: `#{getContext(res)}`"

    setContext res, context

    res.reply "Your current context is changed to `#{context}`"


class Request
  request = require 'request'

  constructor: ->
    caFile = process.env.KUBE_CA
    if caFile and caFile != ""
      fs = require('fs')
      path = require('path')
      @ca = fs.readFileSync(caFile)

    #@tokenMap = generateTokens()

    host = process.env.KUBERNETES_ENDPOINT or 'https://localhost'
    version = process.env.KUBE_VERSION or 'v1'
    @domain = 'https://' + host + '/api/' + version + '/'
    @bearer_token = process.env.KUBERNETES_TOKEN


  getKubeUser: (roles) ->
    # if there is only one token, then return it right away
    if @tokenMap.length is 1
      for role, token of @tokenMap
        return role

    for role in roles
      key = @tokenMap[role]

      if key
        return role

    return ""


  get: ({path, roles}, callback) ->

    requestOptions =
      url : @domain + path

    requestOptions.headers =
      Authorization: 'Bearer ' + @bearer_token

    if @ca
      requestOptions.agentOptions =
        ca: @ca
    console.log @domain + path
    #authOptions = {}
    #user = @getKubeUser roles
    #if user and user isnt ""
    #  requestOptions['auth'] =
    #    user: user
    #    pass: @tokenMap[user]


    request.get requestOptions, (err, response, data) ->

      return callback(err)  if err

      if response.statusCode != 200
        return callback new Error("Status code is not OK: #{response.statusCode}")

      callback null, JSON.parse(data)


  generateTokens = ->
    tokens = {}
    tokensVar = process.env.KUBE_TOKENS
    if not tokensVar or tokensVar is ""
      return tokens

    tokenArr = tokensVar.split(',')
    for token in tokenArr
      keyPair = token.split(":")
      unless keyPair.length is 2
        continue
      tokens[keyPair[0]] = keyPair[1]

    return tokens

timeSince = (date) ->
  d = new Date(date).getTime()
  seconds = Math.floor((new Date() - d) / 1000)

  return "#{Math.floor(seconds)}s"  if seconds < 60

  return "#{Math.floor(seconds/60)}m"  if seconds < 3600

  return "#{Math.floor(seconds/3600)}h"  if seconds < 86400

  return "#{Math.floor(seconds/86400)}d"  if seconds < 2592000

  return "#{Math.floor(seconds/2592000)}mo"  if seconds < 31536000

  return "#{Math.floor(seconds/31536000)}y"
