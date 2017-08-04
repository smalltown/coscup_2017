#!/usr/bin/python
# Copyright 2015 Google Inc. All Rights Reserved.
#
# This file is part of Ansible
#
# Ansible is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Ansible is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Ansible.  If not, see <http://www.gnu.org/licenses/>

DOCUMENTATION = '''
---
module: k8s
version_added: "2.0"
short_description: Manage Kubernetes resources.
description:
    - This module can manage Kubernetes resources on an existing cluster using
      the Kubernetes server API. Users can specify in-line API data, or
      specify an existing Kubernetes YAML file. Currently, this module,
        Only supports HTTP basic authentication method (http://goo.gl/37sh4n)
options:
  api_endpoint:
    description:
      - The IPv4 API endpoint of the Kubernetes cluster.
    required: true
    default: null
    aliases: ["endpoint"]
  file_reference:
    description:
      - Specify full path to a Kubernets YAML file to send to API I(endpoint).
        This option is mutually exclusive with C('inline_data').
    required: true
    default: null
  update_file_reference:
    description:
      - Specify full path to a JSON PATCH YAML file to send to API I(endpoint).
    required: false
    default: null
  bearer_token:
    description:
      - Use the bearer_token as the authentication way to communicat with Kubernetes server.
    required: false
    default: null
  validate_certs:
    description:
      - skip the certificate validation or not
    required: false
    default: false
  state:
    description:
      - The desired action to take on the Kubernetes data.
    required: true
    default: "present"
    choices: ["present", "absent", "update", "replace", "deploy"]

author: "Eric Johnson (@erjohnso) <erjohnso@google.com>"
'''

EXAMPLES = '''
# Create a new namespace from a YAML file.
- name: Create a kubernetes namespace
  kubernetes:
    api_endpoint: 123.45.67.89
    bearer_token: OEMJK0FJKkdf8dJKLfdDF
    file_reference: /path/to/create_namespace.yaml
    update_file_reference: /path/to/update_json_patch.yaml
    validate_certs: true
    state: present
'''

RETURN = '''
# Example response from creating a Kubernetes Namespace.
api_response:
    description: Raw response from Kubernetes API, content varies with API.
    returned: success
    type: dictionary
    contains:
        apiVersion: "v1"
        kind: "Namespace"
        metadata:
            creationTimestamp: "2016-01-04T21:16:32Z"
            name: "test-namespace"
            resourceVersion: "509635"
            selfLink: "/api/v1/namespaces/test-namespace"
            uid: "6dbd394e-b328-11e5-9a02-42010af0013a"
        spec:
            finalizers:
                - kubernetes
        status:
            phase: "Active"
'''

import yaml
############################################################################
############################################################################
# For API coverage, this Anislbe module provides capability to operate on
# all Kubernetes objects that support a "create" call (except for 'Events').
# In order to obtain a valid list of Kubernetes objects, the v1 spec file
# was referenced and the below python script was used to parse the JSON
# spec file, extract only the objects with a description starting with
# 'create a'. The script then iterates over all of these base objects
# to get the endpoint URL and was used to generate the KIND_URL map.
#
# import json
# from urllib2 import urlopen
#
# r = urlopen("https://raw.githubusercontent.com/kubernetes"
#            "/kubernetes/master/api/swagger-spec/v1.json")
# v1 = json.load(r)
#
# apis = {}
# for a in v1['apis']:
#     p = a['path']
#     for o in a['operations']:
#         if o["summary"].startswith("create a") and o["type"] != "v1.Event":
#             apis[o["type"]] = p
#
# def print_kind_url_map():
#     results = []
#     for a in apis.keys():
#         results.append('"%s": "%s"' % (a[3:].lower(), apis[a]))
#     results.sort()
#     print "KIND_URL = {"
#     print ",\n".join(results)
#     print "}"
#
# if __name__ == '__main__':
#     print_kind_url_map()
############################################################################
############################################################################

KIND_URL = {
    "binding": "/api/v1/namespaces/{namespace}/bindings",
    "endpoints": "/api/v1/namespaces/{namespace}/endpoints",
    "limitrange": "/api/v1/namespaces/{namespace}/limitranges",
    "namespace": "/api/v1/namespaces",
    "node": "/api/v1/nodes",
    "persistentvolume": "/api/v1/persistentvolumes",
    "persistentvolumeclaim": "/api/v1/namespaces/{namespace}/persistentvolumeclaims",  # NOQA
    "pod": "/api/v1/namespaces/{namespace}/pods",
    "podtemplate": "/api/v1/namespaces/{namespace}/podtemplates",
    "replicationcontroller": "/api/v1/namespaces/{namespace}/replicationcontrollers",  # NOQA
    "resourcequota": "/api/v1/namespaces/{namespace}/resourcequotas",
    "secret": "/api/v1/namespaces/{namespace}/secrets",
    "service": "/api/v1/namespaces/{namespace}/services",
    "serviceaccount": "/api/v1/namespaces/{namespace}/serviceaccounts",
    "deployment": "/apis/extensions/v1beta1/namespaces/{namespace}/deployments",
    "daemonset": "/apis/extensions/v1beta1/namespaces/{namespace}/daemonsets",
    "horizontalpodautoscaler": "/apis/autoscaling/v1/namespaces/{namespace}/horizontalpodautoscalers",
    "job": "/apis/batch/v1/namespaces/{namespace}/jobs"
}
USER_AGENT = "ansible-k8s-custom-module/0.0.1"


# TODO(erjohnso): SSL Certificate validation is currently unsupported.
# It can be made to work when the following are true:
# - Ansible consistently uses a "match_hostname" that supports IP Address
#   matching. This is now true in >= python3.5.0. Currently, this feature
#   is not yet available in backports.ssl_match_hostname (still 3.4).
# - Ansible allows passing in the self-signed CA cert that is created with
#   a kubernetes master. The lib/ansible/module_utils/urls.py method,
#   SSLValidationHandler.get_ca_certs() needs a way for the Kubernetes
#   CA cert to be passed in and included in the generated bundle file.
# When this is fixed, the following changes can be made to this module,
# - Remove the 'return' statement in line 254 below
# - Set 'required=true' for certificate_authority_data and ensure that
#   ansible's SSLValidationHandler.get_ca_certs() can pick up this CA cert
# - Set 'required=true' for the validate_certs param.

def api_request(module, url, method="GET", headers=None, data=None):
    body = None
    if data:
        data = json.dumps(data)
        
    response, info = fetch_url(module, url, method=method, headers=headers, data=data)
    if int(info['status']) == -1:
        module.fail_json(msg="Failed to execute the API request: %s" % info['msg'], url=url, method=method, headers=headers)
    if response is not None:
        body = json.loads(response.read())
    return info, body


def k8s_create_resource(module, url, authorization, data):

    name = data["metadata"].get("name", None)

    info, body = api_request(module, url + "/" + name, headers={"Authorization": authorization})
    
    if info['status'] == 404:
        info, body = api_request(module, url, method="POST", data=data, headers={"Content-Type": "application/json", "Authorization": authorization})
        if info['status'] == 409:
            name = data["metadata"].get("name", None)
            info, body = api_request(module, url + "/" + name, headers={"Authorization": authorization})
            return False, body
        elif info['status'] >= 400:
            module.fail_json(msg="failed to create the resource: %s" % info['msg'], url=url)

    elif info['status'] == 200:
        # the resource is already exist
        print 'the resource is already exist'
    else:
        module.fail_json(msg="failed to get the resource info: %s" % info['msg'], url=url)

    return True, body

     
def k8s_delete_resource(module, url, authorization, data):
    name = data.get('metadata', {}).get('name')
    if name is None:
        module.fail_json(msg="Missing a named resource in object metadata when trying to remove a resource")

    url = url + '/' + name
    info, body = api_request(module, url, method="DELETE", headers={"Authorization": authorization})
    if info['status'] == 404:
        return False, "Resource name '%s' already absent" % name
    elif info['status'] >= 400:
        module.fail_json(msg="failed to delete the resource '%s': %s" % (name, info['msg']), url=url)
    return True, "Successfully deleted resource name '%s'" % name


def k8s_replace_resource(module, url, authorization, data):
    name = data.get('metadata', {}).get('name')
    if name is None:
        module.fail_json(msg="Missing a named resource in object metadata when trying to replace a resource")

    headers = {"Content-Type": "application/json", "Authorization": authorization}
    url = url + '/' + name
    info, body = api_request(module, url, method="PUT", data=data, headers=headers)
    if info['status'] == 409:
        name = data["metadata"].get("name", None)
        info, body = api_request(module, url + "/" + name)
        return False, body
    elif info['status'] >= 400:
        module.fail_json(msg="failed to replace the resource '%s': %s" % (name, info['msg']), url=url)
    return True, body


def k8s_update_resource(module, url, authorization, data):
    name = data.get('metadata', {}).get('name')
    if name is None:
        module.fail_json(msg="Missing a named resource in object metadata when trying to update a resource")

    headers = {"Content-Type": "application/strategic-merge-patch+json", "Authorization": authorization}
    url = url + '/' + name
    info, body = api_request(module, url, method="PATCH", data=data, headers=headers)
    if info['status'] == 409:
        name = data["metadata"].get("name", None)
        info, body = api_request(module, url + "/" + name)
        return False, body
    elif info['status'] >= 400:
        module.fail_json(msg="failed to update the resource '%s': %s" % (name, info['msg']), url=url)
    return True, body


def main():
    module = AnsibleModule(
        argument_spec=dict(
            http_agent=dict(default=USER_AGENT),
            
            bearer_token=dict(default="", no_log=True),
            validate_certs = dict(default='no', type='bool'),
            api_endpoint=dict(required=True),
            file_reference=dict(required=True),
            update_file_reference=dict(required=False, default=None),
            state=dict(default="present", choices=["present", "absent", "update", "replace"])
        ),
    )

    api_endpoint = module.params.get('api_endpoint')
    state = module.params.get('state')
    bearer_token = module.params.get('bearer_token')
    inline_data = module.params.get('inline_data')
    file_reference = module.params.get('file_reference')
    update_file_reference = module.params.get('update_file_reference')

    try:
        f = open(file_reference, "r")
        data = [x for x in yaml.load_all(f)]
        f.close()
        if not data:
            module.fail_json(msg="No valid data could be found.")
        
        if update_file_reference is not None:
            f = open(update_file_reference, "r")
            update_data = [x for x in yaml.load_all(f)]
            f.close()

            if not update_data:
                module.fail_json(msg="No valid update data could be found.")
    except:
      module.fail_json(msg="The file '%s' was not found or contained invalid YAML/JSON data" % file_reference)

    # set the transport type and build the target endpoint url
    transport = 'https'

    target_endpoint = "%s://%s" % (transport, api_endpoint)

    authorization = "Bearer " + bearer_token

    body = []
    changed = False

    # make sure the data is a list
    if not isinstance(data, list):
        data = [ data ]

    for item in data:
        namespace = "default"
        if item and 'metadata' in item:
            namespace = item.get('metadata', {}).get('namespace', "default")
            kind = item.get('kind', '').lower()
            try:
                url = target_endpoint + KIND_URL[kind]
            except KeyError:
                module.fail_json("invalid resource kind specified in the data: '%s'" % kind)
            url = url.replace("{namespace}", namespace)
        else:
            url = target_endpoint

        if state == 'present':
            item_changed, item_body = k8s_create_resource(module, url, authorization, item)
        elif state == 'absent':
            item_changed, item_body = k8s_delete_resource(module, url, authorization, item)
        elif state == 'replace':
            item_changed, item_body = k8s_replace_resource(module, url, authorization, item)
        elif state == 'update':
            item_changed, item_body = k8s_update_resource(module, url, authorization, item)

        
        changed |= item_changed
        body.append(item_body)

    module.exit_json(changed=changed, api_response=body)


# import module snippets
from ansible.module_utils.basic import *    # NOQA
from ansible.module_utils.urls import *     # NOQA


if __name__ == '__main__':
    main()


