/**
 *  La Marzocco Espresso Machine driver for Hubitat
 *
 *  Copyright 2024 Derek Choate
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  1/11/2024 V0.9.0-alpha Alpha version with support for Machine State (on/off), Espresso Boiler temperature, and Water Level
 *  31/7/2025 v2.0.0-alpha Alpha version with support for latest gateway firmware (v5.2.7)
 */

import groovy.json.*
import java.time.*
import java.time.format.*

def libversion() {"2.0.0-alpha"}

library (
    author: "Derek Choate",
    category: "La Marzocco Home Espresso Machine",
    description: "Constants and other common methods to support integration with La Marzocco Home Espresso Machines",
    name: "lmCommon",
    namespace: "derekchoate",
    documentationLink: "https://raw.githubusercontent.com/derekchoate/hubitat-lamarzocco-driver/release/latest/README.md"
)

String getConstant(String name) {
    Map<String, String> CONSTANTS = [
        "THING_TYPE_COFFEE_MACHINE" : "CoffeeMachine",
        "COMMAND_POWER_CHANGE_STATE" : "CoffeeMachineChangeMode",
        "POWER_STATE_ACTIVE" : "BrewingMode",
        "POWER_STATE_STANDBY" : "StandBy"
    ]

    if (!CONSTANTS.containsKey(name)) {
        throw new Exception("Constant ${name} is not defined")
    }

    return CONSTANTS[name]
}

String getEndpoint(String name, Map<String, String> parameters) {
    log.trace("getEndpoint")

    Map<String, String> ENDPOINTS = [
        "LOGIN" : "https://:apiHost:customerEndpoint/auth/signin",
        "REFRESH_TOKEN" : "https://:apiHost:customerEndpoint/auth/refreshtoken",
        "THINGS" : "https://:apiHost:customerEndpoint/things",
        "DASHBOARD" : "https://:apiHost:customerEndpoint/things/:serialNumber/dashboard",
        "SETTINGS" : "https://:apiHost:customerEndpoint/things/:serialNumber/settings",
        "COMMAND" : "https://:apiHost:customerEndpoint/things/:serialNumber/command/:command",
        "STREAMING" : "wss://:apiHost:streamingEndpoint"
    ]

    if (!ENDPOINTS.containsKey(name)) {
        throw new Exception("Endpoint ${name} is not defined")
    }

    String formattedEndpoint = ENDPOINTS[name]
    
    for (String key in parameters.keySet()) {
        formattedEndpoint = formattedEndpoint.replaceAll("\\:${key}", parameters[key])
    }

    log.trace("getEndpoint (returning)")

    return formattedEndpoint
}

void sendJsonPost(String endpointName, Map<String, String> parameters, String bearerToken, Object body, Closure responseHandler) {
    log.trace("sendJsonPost")
    
    String endpoint = getEndpoint(endpointName, parameters)
    String bodyText = new JsonBuilder(body).toString()

    try {
        httpPostJson(uri: endpoint,
                requestContentType: "application/json",
                headers: [
                    "Authorization" : "Bearer ${bearerToken}"
                ],
                body: bodyText) 
        
        {response -> 
            log.trace("sendJsonPost response closure")

            responseHandler(response)

            log.trace("sendJsonPost response closure (completed)")
        }
    }
    catch (Exception ex) {
        log.error("An error occurred whilst calling the endpoint ${endpoint}")
        throw ex
    }

    log.trace("sendJsonPost (complete)")
}

void getJson(String endpointName, Map<String, String> parameters, String bearerToken, Closure responseHandler) {
    log.trace("getJson")

    String endpoint = getEndpoint(endpointName, parameters)

    try {
        httpGet(uri: endpoint,
            contentType: "application/json",
            headers: [
                "Authorization" : "Bearer ${bearerToken}"
            ]) 
            
        {response -> 
            log.trace("getJson response closure")

            responseHandler(response)

            log.trace("getJson response closure (complete)")
        }
    }
    catch (Exception ex) {
        log.error("An error occurred whilst calling the endpoint ${endpoint}")
        throw ex
    }

    log.trace("getJson (complete)")
    
}

void refreshAccessToken(String apiHost, String customerEndpoint, String username, String refreshToken, Closure loginHandler) {
    log.trace("refreshAccessToken")

    if (apiHost == null) {
        throw new Exception("apiHost is required")
    }

    if (customerEndpoint == null) {
        throw new Exception("customerEndpoint is required")
    }

    if (username == null) {
        throw new Exception("username is required")
    }

    if (refreshToken == null) {
        throw new Exception("refreshToken is required")
    }

    Map<String, String> credentials = [
        "username" : username, 
        "refreshToken" : refreshToken
    ]

    String bodyText = new JsonBuilder(credentials).toString();

    String tokenEndpoint = getEndpoint("REFRESH_TOKEN", ["apiHost" : apiHost, "customerEndpoint" : customerEndpoint]);
    
    try {
        httpPostJson(uri: tokenEndpoint, 
                    contentType: "application/json; charset=utf-8", 
                    body: bodyText) 
                    
        {response -> 
            log.trace("refreshAccessToken httpPostJson closure")

            loginHandler(response.data.accessToken, calculateExpireyDateTime(3600), response.data.refreshToken)

            log.trace("refreshAccessToken httpPostJson closure (complete)")
        }
    }
    catch (Exception ex) {
        log.error("error ")
        throw new Exception("token expired, please login again", ex)
    }

    log.trace("refreshAccessToken (complete)")
}

void login(String apiHost, String customerEndpoint, String username, String password, Closure loginHandler) {
    log.trace("login")

    if (apiHost == null) {
        throw new Exception("apiHost is required")
    }

    if (customerEndpoint == null) {
        throw new Exception("customerEndpoint is required")
    }

    if (username == null) {
        throw new Exception("username is required")
    }

    if (password == null) {
        throw new Exception("password is required")
    }

    Map<String, Object> credentials = [
        "username" : username,
        "password" : password
    ]

    String bodyText = new JsonBuilder(credentials).toString();

    String loginEndpoint = getEndpoint("LOGIN", ["apiHost" : apiHost, "customerEndpoint" : customerEndpoint]);

    httpPostJson(uri: loginEndpoint, 
                contentType: "application/json; charset=utf-8", 
                body: bodyText) 
                
    {response -> 
        log.trace("login httpPostJson closure")

        loginHandler(response.data.accessToken, calculateExpireyDateTime(3600), response.data.refreshToken)

        log.trace("login httpPostJson closure (completed)")
    }

    log.trace("login (complete)")
}

LocalDateTime calculateExpireyDateTime(Integer expiresIn) {
    log.trace("calculateExpireyDateTime")

    Integer ttl = expiresIn - 10
    LocalDateTime accessTokenExpires = LocalDateTime.now().plusSeconds(ttl)

    log.trace("calculateExpireyDateTime (returning)")

    return accessTokenExpires
}

void executeCommand(String apiHost, String customerEndpoint, String serialNumber, String accessToken, String command, Map<String, Object> commandParameters) {
    log.trace("executeCommand")

    if (apiHost == null) {
        throw new Exception("apiHost is required")
    }
    
    if (customerEndpoint == null) {
        throw new Exception("customerEndpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    if (command == null) {
        throw new Exception("command is required")
    }

    Map<String, String> parameters = ["apiHost" : apiHost, 
                                      "customerEndpoint" : customerEndpoint, 
                                      "serialNumber" : serialNumber,
                                      "command" : command]

    sendJsonPost("COMMAND", parameters, accessToken, commandParameters)

    {response -> 
        log.trace("executeCommand - sendJsonPost - closure")

        if (response.data?.errorCode != null) {
            log.trace("executeCommand (returning early because the request resulted in an error)")
            throw new Exception("An error '${response.data?.errorCode}' occurred whilst executing the command ${command}")
        }

        log.trace("executeCommand - sendJsonPost - closure (complete)")
    }

    log.trace("executeCommand (complete)")
}

void getRegisteredMachines(String apiHost, String customerEndpoint, String accessToken, Closure responseHandler) {
    log.trace("getRegisteredMachines")
    
    if (apiHost == null) {
        throw new Exception("apiHost is required")
    }
    
    if (customerEndpoint == null) {
        throw new Exception("Customer Endpoint is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["apiHost" : apiHost, "customerEndpoint" : customerEndpoint]

    getJson("THINGS", parameters, accessToken)

    {response -> 

        log.trace("getRegisteredMachines getJson closure")

        List<Map<String, Object>> things = response.data;

        Map<String, String> devices = [:]        

        things.findAll{it.type == "CoffeeMachine"}.each {
            devices[it.serialNumber] = "${it.name} (${it.modelName} - ${it.serialNumber})"
        }

        responseHandler(devices)

        log.trace("getRegisteredMachines getJson closure (complete)")
    }

    log.trace("getRegisteredMachines (complete)")
}

void getMachineDashboard(String apiHost, String customerEndpoint, String serialNumber, String accessToken, Closure responseHandler) {
    log.trace("getMachineDashboard")

    if (apiHost == null) {
        throw new Exception("apiHost is required")
    }

    if (customerEndpoint == null) {
        throw new Exception("Customer Endpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["apiHost" : apiHost,
                                      "customerEndpoint" : customerEndpoint,
                                      "serialNumber" : serialNumber];

    getJson("DASHBOARD", parameters, accessToken)
    
    {response -> 

        log.trace("getMachineDashboard getJson closure")

        Map<String, Object> machine = response.data;

        responseHandler(response.data);

        log.trace("getMachineDashboard getJson closure (completed)")
    }

    log.trace("getMachineDashboard (complete)")
}

void getMachineConfig(String apiHost, String customerEndpoint, String serialNumber, String accessToken, Closure responseHandler) {
    log.trace("getMachineConfig")

    if (apiHost == null) {
        throw new Exception("apiHost is required")
    }

    if (customerEndpoint == null) {
        throw new Exception("Customer Endpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["apiHost" : apiHost,
                                      "customerEndpoint" : customerEndpoint,
                                      "serialNumber" : serialNumber];

    getJson("SETTINGS", parameters, accessToken)            
    {response -> 
        log.trace("getMachineConfig getJson closure")

        responseHandler(response.data)

        log.trace("getMachineConfig getJson closure (complete)")
    }

    log.trace("getMachineConfig (complete)")
}

String formatStompMessage(String command, Map<String, String> headers, String body) {
    log.trace("formatStompMessage")

    List<String> fragments = [command]

    headers.each{
        key, value -> 
            fragments.add("${key}:${value}")
    }

    String message = fragments.join("\n") + "\n\n"

    if (body != null && body != "") {
        message += body
    }

    message += "\0"

    log.trace("formatStompMessage (returning)")

    return message
}

void parseStompMessage(String message, Closure handler) {
    log.trace("parseStompMessage")

    if (message == null || message.trim() == "") {
        throw new Exception("message is required")
    }

    String messageType
    Map<String, String> headers = [:]
    String body

    String[] fragments = message.split("\n")

    messageType = fragments[0]

    if (!["CONNECTED", "MESSAGE", "RECEIPT", "ERROR"].contains(messageType)) {
        throw new Exception("Message is malformed - expecting the first line to contain a valid messageType, but received ${messageType}")
    }

    Integer i = 1;

    log.trace("parseStompMessage - completed parsing messageType, starting headers")

    while (fragments.length > i && fragments[i] != null && fragments[i] != "" && fragments[i] != "\0") {
        (headerName, headerValue) = parseStompHeader(fragments[i])
        headers[headerName] = headerValue
        ++i
    }

    log.trace("parseStompMessage - completed parsing headers, starting body")

    if (fragments[i] != "\0") {
        body = ""
        while (fragments.length > i && fragments[i] != "\0") {
            if (fragments[i] != null) {
                body += fragments[i]
            }
            body += "\n"
            ++i
        }
    }

    handler(messageType, headers, body)

    log.trace("parseStompMessage (complete)")
}

List<String> parseStompHeader(String header) {
    log.trace("parseStompHeader")

    if (header == null || header.trim() == "") {
        log.trace("parseStompHeader (complete)")

        return [null, null]
    }

    if (!header.contains(":")) {
        log.trace("parseStompHeader (complete)")

        return [header, null]
    }

    log.trace("parseStompHeader (returning)")

    return header.tokenize(":")
}