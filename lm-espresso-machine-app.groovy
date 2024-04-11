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
 *  4/11/2024 V0.9.1-alpha Alpha fixed bug in access token refresh handling
 */

#include derekchoate.lmCommon
import com.hubitat.app.ChildDeviceWrapper
import groovy.transform.Field

def version() {"0.9.1-alpha"}

@Field final Boolean debug = true

definition(
    name: "La Marzocco Home Espresso Machine Setup App",
    namespace: "derekchoate",
    author: "Derek Choate",
    description: "Configures the driver for the La Marzocco Espresso Machine",
    category: "Drivers",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "start", title: "La Marzocco Home Integration", content: "startPage", install: false)
	page(name: "credentials", title: "Fetch La Marzocco Credentials", content: "authPage", install: false)
	page(name: "loginInterstitial", title: "La Marzocco Home Integration", content: "loginInterstitialPage")
	page(name: "listDevices", title: "Espresso Machines", content: "listDevicesPage", install: false)
	page(name: "complete", title: "La Marzocco Home is now connected to Hubitat!", content: "completePage", uninstall: true)
	page(name: "invalidCredentials", title: "Invalid Credentials", content: "badAuthPage", install: false)
}

def startPage() {
    log.trace("startPage")
    if (state.accessToken == null) {
        return authPage()
    }
    else {
        return listDevicesPage()
    }
}

def authPage() {
    log.trace("authPage")

	def description = "Tap to enter Credentials."
	return dynamicPage(name: "Credentials", title: "Authorize Connection", nextPage:"loginInterstitial", uninstall: false , install:false) {
	   section("La Marzocco Home Credentials") {
            input (name: "username", type: "text", title: "Your La Marzocco Username", required: true)
            input (name: "password", type: "password", title: "Your La Marzocco Password", required: true)
        }
        section(hideable: true, hidden: true, "Advanced Settings") {
            input (name: "clientId", type: "text", title: "Client Id", defaultValue: "7_1xwei9rtkuckso44ks4o8s0c0oc4swowo00wgw0ogsok84kosg", required: true)
            input (name: "clientSecret", type: "text", title: "Client Secret", defaultValue: "2mgjqpikbfuok8g4s44oo4gsw0ks44okk4kc4kkkko0c8soc8s", required: true)
            input (name: "tokenEndpoint", type: "text", title: "Token Endpoint", defaultValue: "https://cms.lamarzocco.io/oauth/v2/token", required: true)
            input (name: "customerEndpoint", type: "text", title: "Customer Endpoint", defaultValue: "https://cms.lamarzocco.io/api/customer", required: true)
            input (name: "deviceEndpoint", type: "text", title: "Device Endpoint", defaultValue: "https://gw-lmz.lamarzocco.io/v1/home/machines", required: true)
        }
	}
}

def loginInterstitialPage() {
    log.trace("loginInterstitialPage")

    if (settings.username == null) {
        throw new Exception("Username is required")
    }

    if (settings.password == null) {
        throw new Exception("Password is required")
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    LocalDateTime accessTokenExpires = LocalDateTime.now()

    //if the access token is still valid, do nothing
    if (state.accessTokenExpires != null) {
        accessTokenExpires = LocalDateTime.parse(state.accessTokenExpires.substring(10), formatter)
    }

 	if (state.accessToken == null || LocalDateTime.now().isAfter(accessTokenExpires)){
        try {
            login(settings.tokenEndpoint, settings.username, settings.password, settings.clientId, settings.clientSecret) {
                newAccessToken, newAccessTokenExpires, newRefreshToken ->

                state.accessToken = newAccessToken
                state.accessTokenExpires = "DateTime: ${newAccessTokenExpires.format(formatter)}"
                state.refreshToken = newRefreshToken
            }
        }
        catch (Exception ex) {
            log.error(ex)
        }
    }

    app.removeSetting("password")

    if (state.accessToken != null) {
        return listDevicesPage()
    }
    else {
        return badAuthPage()
    }
}

def listDevicesPage() {
    log.trace("listDevicesPage")

    try {
        refreshAccessToken()
    }
    catch (Exception ex) {
        return authPage()
    }

    Map<String, String> options = null

	getRegisteredMachines(settings.customerEndpoint, state.accessToken) {
        machines -> options = machines
    }

	dynamicPage(name: "listDevices", title: "Choose Espresso Machines", install:false, uninstall:false, nextPage: "complete") {
		section("Espresso Machines") {
			input "espressoMachines", "enum", title: "Select Machine(s)", required: false, multiple: true, options: options
		}
	}
}

def completePage() {
    log.trace("completePage")

    Map<String, ChildDeviceWrapper> childDevices = getChildDevicesBySerialNumber()
    
    settings.espressoMachines.each {
        if (!childDevices.containsKey(it)) {
            addEspressoMachine(it)
        }
    }

    childDevices.keySet().each {
        if (settings.espressoMachines == null || !settings.espressoMachines.contains(it)) {
            removeEspressoMachine(childDevices[it])
        }
    }

    return dynamicPage(name: "Complete", title: "Setup Complete", install:true, uninstall: false) {
        section("Complete") {
            paragraph "The devices have been setup successfully"
        }
    }
}

def badAuthPage(){
    log.trace("badAuthPage")

    log.error "Unable to get access token"
    return dynamicPage(name: "badCredentials", title: "Invalid Username and Password", install:false, uninstall:true, nextPage: authPage) {
        section("Error") {
            paragraph "Please check your username and password"
        }
    }
}

Map<String, ChildDeviceWrapper> getChildDevicesBySerialNumber() {
    log.trace("getChildDevicesBySerialNumber")

    Map<String, ChildDeviceWrapper> childDevices = [:]

    getAllChildDevices().each {
        String serialNumber = it.getSetting("serialNumber")

        if (serialNumber == null) {
            log.error "Device ${it.getDisplayName()} (${it.getDeviceNetworkId()}) does not have a serial number set"
        }

        childDevices[serialNumber] = it
    }

    return childDevices

}

void addEspressoMachine(String serialNumber) {
    log.trace("addEspressoMachine")

    ChildDeviceWrapper newDevice = addChildDevice("derekchoate", "La Marzocco Home Espresso Machine", serialNumber)
    newDevice.updateSetting("serialNumber", serialNumber)
    newDevice.initialize()
}

void removeEspressoMachine(ChildDeviceWrapper device) {
    log.trace("removeEspressoMachine")

    deleteChildDevice(device.getDeviceNetworkId())
}

void refreshAccessToken() {
    log.trace("refreshAccessToken")

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    //if the access token is still valid, do nothing
    if (state.accessTokenExpires != null) {
        LocalDateTime accessTokenExpires = LocalDateTime.parse(state.accessTokenExpires.substring(10), formatter)
        
        if (LocalDateTime.now().isBefore(accessTokenExpires)) {
            //token still valid
            return
        }
    }

    String tokenEndpoint = settings?.tokenEndpoint
    String refreshToken = settings?.refreshToken
    String clientId = settings?.clientId
    String clientSecret = settings?.clientSecret

    if (tokenEndpoint == null) {
        throw new Exception("tokenEndpoint is required to refresh the access token")
    }

    if (state.refreshToken != null) {
        refreshToken = state.refreshToken
    }

    if (refreshToken == null) {
        throw new Exception("tokenEndpoint is required to refresh the access token")
    }

    if (clientId == null) {
        throw new Exception("clientId is required to refresh the access token")
    }

    if (clientSecret == null) {
        throw new Exception("clientSecret is required to refresh the access token")
    }

    try {
        refreshAccessToken(tokenEndpoint, refreshToken, clientId, clientSecret) 
        {   accessToken, accessTokenExpires, newRefreshToken ->
            state.accessToken = accessToken
            state.accessTokenExpires = "DateTime: " + accessTokenExpires.format(formatter)
            state.refreshToken = newRefreshToken
        }
    }
    catch (Exception ex) {
        app.removeSetting("refreshToken")
        app.removeSetting("accessTokenExpires")
        throw ex
    }
}

void refreshMachineDetails(String serialNumber, Closure responseHandler) {
    log.trace("refreshMachineDetails")

    if (settings?.customerEndpoint == null) {
        throw new Exception("Customer Endpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    refreshAccessToken()

    if (state.accessToken == null) {
        throw new Exception("There is no access token stored in state")
    }

    log.trace("(in app) getting machine details for ${serialNumber}")

    getMachineDetails(settings?.customerEndpoint, serialNumber, state.accessToken) 

    {communicationKey, modelName -> 
        responseHandler(communicationKey, modelName)
    }
}

void refreshMachineIpAddress(String serialNumber, Closure responseHandler) {
    log.trace("refreshMachineIpAddress")

    if (settings?.deviceEndpoint == null) {
        throw new Exception("Device Endpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    refreshAccessToken()

    if (state.accessToken == null) {
        throw new Exception("There is no access token stored in state")
    }

    getMachineIpAddress(settings?.deviceEndpoint, serialNumber, state.accessToken)
    {ipAddress -> 
        responseHandler(ipAddress)
    }
}


void setMachineStatus(String serialNumber, String status) {
    log.trace("setMachineStatus")

    if (settings?.deviceEndpoint == null) {
        throw new Exception("Device Endpoint is required")
    }

    if (serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    if (status == null) {
        throw new Exception("state is required")
    }

    refreshAccessToken()

    if (state.accessToken == null) {
        throw new Exception("There is no access token stored in state")
    }

    setMachineStatus(settings?.deviceEndpoint, serialNumber, state.accessToken, status)
}
