/**
 *  La Marzocco Espresso Machine driver for Hubitat
 *
 *  Copyright 2016 Stuart Buchanan
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
 */

import groovy.json.*
import java.time.*
import java.time.format.*

def libversion() {"0.9.0-alpha"}

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
        "MACHINE_STATUS_ON" : "BrewingMode",
        "MACHINE_STATUS_OFF" : "StandBy"
    ]

    if (!CONSTANTS.containsKey(name)) {
        throw new Exception("Constant ${name} is not defined")
    }

    return CONSTANTS[name]
}

String getEndpoint(String name, Map<String, String> parameters) {
    Map<String, String> ENDPOINTS = [
        "STATUS" : ":deviceEndpoint/:serialNumber/status",
        "LAST_ONLINE" : ":deviceEndpoint/:serialNumber/last-online",
        "CUSTOMER" : ":customerEndpoint",
        "LOCAL_CONFIG" : "http://:localIpAddress:8081/api/v1/config",
        "STREAMING" : "ws://:localIpAddress:8081/api/v1/streaming"
    ]

    if (!ENDPOINTS.containsKey(name)) {
        throw new Exception("Endpoint ${name} is not defined")
    }
    String formattedEndpoint = ENDPOINTS[name]
    for (String key in parameters.keySet()) {
        formattedEndpoint = formattedEndpoint.replaceAll("\\:${key}", parameters[key])
    }
    return formattedEndpoint
}

void sendJsonPost(String endpointName, Map<String, String> parameters, String bearerToken, Object body, Closure responseHandler) {
    String endpoint = getEndpoint(endpointName, parameters)
    String bodyText = new JsonBuilder(body).toString()

    Object responseData = null

    httpPostJson(uri: endpoint,
            requestContentType: "application/json",
            headers: [
                "Authorization" : "Bearer ${bearerToken}"
            ],
            body: bodyText) 
    
    {response -> 
    responseHandler(response)
    }
}

void getJson(String endpointName, Map<String, String> parameters, String bearerToken, Closure responseHandler) {
    String endpoint = getEndpoint(endpointName, parameters)

    Object responseData = null

    httpGet(uri: endpoint,
            contentType: "application/json",
            headers: [
                "Authorization" : "Bearer ${bearerToken}"
            ]) 
            
    {response -> 
        responseHandler(response)
    }
}

void refreshAccessToken(String tokenEndpoint, String refreshToken, String clientId, String clientSecret, Closure loginHandler) {

    if (tokenEndpoint == null) {
        throw new Exception("tokenEndpoint is required")
    }

    if (refreshToken == null) {
        throw new Exception("refreshToken is required")
    }

    if (clientId == null) {
        throw new Exception("clientId is required")
    }

    if (clientSecret == null) {
        throw new Exception("clientSecret is required")
    }

    String body = "client_id=${clientId}&client_secret=${clientSecret}&grant_type=refresh_token&refresh_token=${refreshToken}"

    httpPostJson(uri: tokenEndpoint, 
                requestContentType: "application/x-www-form-urlencoded", 
                body: body) 
                
    {response -> 
        loginHandler(response.data.access_token, calculateExpireyDateTime(response.data.expires_in), response.data.refresh_token)
    }
}

void login(String tokenEndpoint, String username, String password, String clientId, String clientSecret, Closure loginHandler) {

    if (tokenEndpoint == null) {
        throw new Exception("tokenEndpoint is required")
    }

    if (username == null) {
        throw new Exception("username is required")
    }

    if (password == null) {
        throw new Exception("password is required")
    }

    if (clientId == null) {
        throw new Exception("clientId is required")
    }

    if (clientSecret == null) {
        throw new Exception("clientSecret is required")
    }

    String body = "client_id=${clientId}&client_secret=${clientSecret}&grant_type=password&username=${username}&password=${password}"

    httpPostJson(uri: tokenEndpoint, 
                requestContentType: "application/x-www-form-urlencoded", 
                body: body) 
                
    {response -> 
        loginHandler(response.data.access_token, calculateExpireyDateTime(response.data.expires_in), response.data.refresh_token)
    }
}

LocalDateTime calculateExpireyDateTime(Integer expiresIn) {
    Integer ttl = expiresIn - 10
    LocalDateTime accessTokenExpires = LocalDateTime.now().plusSeconds(ttl)
}

void getMachineStatus(String deviceEndpoint, String serialNumber, String accessToken, Closure responseHandler) {
    if (deviceEndpoint == null) {
        throw new Exception("deviceEndpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["deviceEndpoint" : deviceEndpoint, 
                                    "serialNumber" : serialNumber]

    getJson("STATUS", parameters, accessToken)

    {response -> 
        responseHandler(response.data.data.MACHINE_STATUS, response.data.data.LEVEL_TANK)
    }
}

void setMachineStatus(String deviceEndpoint, String serialNumber, String accessToken, String status) {
    if (deviceEndpoint == null) {
        throw new Exception("deviceEndpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    if (status == null) {
        throw new Exception("status is required")
    }

    Map<String, String> parameters = ["deviceEndpoint" : deviceEndpoint, 
                                    "serialNumber" : serialNumber]

    Map<String, Object> body = ["status" : status]

    sendJsonPost("STATUS", parameters, accessToken, body)

    {response -> 
        if (response.data.status == true) {
            //log.info("Status set successfully")
        }
        else {
            throw new Exception("Failed to set stastus")
        }
    }
}

void getRegisteredMachines(String customerEndpoint, String accessToken, Closure responseHandler) {
    if (customerEndpoint == null) {
        throw new Exception("Customer Endpoint is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["customerEndpoint" : customerEndpoint]

    getJson("CUSTOMER", parameters, accessToken)

    {response -> 

        List<Map<String, Object>> fleet = response.data.data.fleet

        Map<String, String> devices = [:]

        fleet.each {
            devices[it.machine.serialNumber] = "${it.machine.model.name} (${it.machine.serialNumber})"
        }

        responseHandler(devices)
    }
}

void getMachineDetails(String customerEndpoint, String serialNumber, String accessToken, Closure responseHandler) {
    if (customerEndpoint == null) {
        throw new Exception("Customer Endpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["customerEndpoint" : customerEndpoint]

    getJson("CUSTOMER", parameters, accessToken)
    
    {response -> 

        List<Map<String, Object>> fleet = response.data.data.fleet

        Map<String, Object> machine = fleet.find({
            it.machine.serialNumber == serialNumber
        })

        if (machine == null) {
            throw new Exception("No machine found with the supplied serial number")
            return
        }

        responseHandler(machine.communicationKey, machine.machine.model.name)
    }
}

void getMachineIpAddress(String deviceEndpoint, String serialNumber, String accessToken, Closure responseHandler) {
    if (deviceEndpoint == null) {
        throw new Exception("deviceEndpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber is required")
    }

    if (accessToken == null) {
        throw new Exception("accessToken is required")
    }

    Map<String, String> parameters = ["deviceEndpoint" : deviceEndpoint, 
                                    "serialNumber" : serialNumber]

    getJson("LAST_ONLINE", parameters, accessToken)

    {response -> 
        responseHandler(response.data.data.ip)
    }
}

void getMachineConfig(String localIpAddress, String communicationKey, Closure responseHandler) {
    if (localIpAddress == null) {
        throw new Exception("Local IP Address is required")
    }

    if (communicationKey == null) {
        throw new Exception("communicationKey is required")
    }

    Map<String, String> parameters = ["localIpAddress" : localIpAddress]

    getJson("LOCAL_CONFIG", parameters, communicationKey)            
    {response -> 
        responseHandler(response.data)
    }
}