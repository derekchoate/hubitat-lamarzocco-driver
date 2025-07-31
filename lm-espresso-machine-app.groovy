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
 *  31/7/2025 v2.0.0-alpha Alpha version with support for latest gateway firmware (v5.2.7)
 */

#include derekchoate.lmCommon
import com.hubitat.app.ChildDeviceWrapper
import groovy.transform.Field

def version() {"2.0.0-alpha"}

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
        log.trace("startPage (returning authPage)")
        return authPage()
    }
    else {
        log.trace("startPage (returning listDevicesPage)")
        return listDevicesPage()
    }
}

def authPage() {
    log.trace("authPage")

	def description = "Tap to enter Credentials."

    log.trace("authPage (returning)")

	return dynamicPage(name: "Credentials", title: "Authorize Connection", nextPage:"loginInterstitial", uninstall: false , install:false) {
	   section("La Marzocco Home Credentials") {
            input (name: "username", type: "text", title: "Your La Marzocco Username", required: true)
            input (name: "password", type: "password", title: "Your La Marzocco Password", required: true)
        }
        section(hideable: true, hidden: true, "Advanced Settings") {
            input (name: "apiHost", type: "text", title: "API Host", defaultValue: "lion.lamarzocco.io", required: true)
            input (name: "customerEndpoint", type: "text", title: "Customer API Endpoint", defaultValue: "/api/customer-app", required: true)
            input (name: "streamingEndpoint", type: "text", title: "Streaming API Endpoint", defaultValue: "/ws/connect", required: true)
        }
	}
}

def loginInterstitialPage() {
    log.trace("loginInterstitialPage")

    if (settings?.apiHost == null) {
        throw new Exception("apiHost is required")
    }

    if (settings?.customerEndpoint == null) {
        throw new Exception("customerEndpoint is required")
    }

    if (settings?.username == null) {
        throw new Exception("Username is required")
    }

    if (settings?.password == null) {
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
            login(settings.apiHost, settings.customerEndpoint, settings.username, settings.password) {
                newAccessToken, newAccessTokenExpires, newRefreshToken ->

                log.trace("login - login - closure")

                state.accessToken = newAccessToken
                state.accessTokenExpires = "DateTime: ${newAccessTokenExpires.format(formatter)}"
                state.refreshToken = newRefreshToken

                log.trace("login - login - closure (complete)")
            }
        }
        catch (Exception ex) {
            log.error(ex)
        }
    }

    app.removeSetting("password")

    log.trace("loginInterstitialPage (returning)")

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
        log.error("An exception occurred whilst refreshing the access token")
        log.error(ex)
        return authPage()
    }

    //List of coffee machines that have been registered with the cloud service
    Map<String, String> coffeeMachines
    
    //Retreive the list of registered machines
	getRegisteredMachines(settings.apiHost, settings.customerEndpoint, state.accessToken) {
        things -> 
            coffeeMachines = things
    }

    log.info(coffeeMachines)

    log.trace("listDevicesPage (returning)")

	return dynamicPage(name: "listDevices", title: "Choose Espresso Machines", install:false, uninstall:false, nextPage: "complete") {
        section("Espresso Machines") {
        	input "espressoMachines", "enum", 
                title: "Select Machine(s)", 
                required: false, 
                multiple: true, 
                options: coffeeMachines
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

    log.trace("completePage (returning)")

    return dynamicPage(name: "Complete", title: "Setup Complete", install:true, uninstall: false) {
        section("Complete") {
            paragraph "The devices have been setup successfully"
        }
    }
}

def badAuthPage(){
    log.trace("badAuthPage")

    log.error "Unable to get access token"

    log.trace("badAuthPage (returning)")

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

    log.trace("getChildDevicesBySerialNumber (returning)")

    return childDevices

}

List<String> getChildDeviceSerialNumbers() {
    log.trace("getChildDeviceSerialNumbers")

    List<String> childDeviceSerialNumbers = []

    getAllChildDevices().each {
        String serialNumber = it.getSetting("serialNumber")

        if (serialNumber == null) {
            log.error "Device ${it.getDisplayName()} (${it.getDeviceNetworkId()}) does not have a serial number set"
        }

        childDeviceSerialNumbers.add(serialNumber)
    }

    log.trace("getChildDeviceSerialNumbers (returning)")

    return childDeviceSerialNumbers

}

void addEspressoMachine(String serialNumber) {
    log.trace("addEspressoMachine")

    ChildDeviceWrapper newDevice = addChildDevice("derekchoate", "La Marzocco Home Espresso Machine", serialNumber)
    newDevice.updateSetting("serialNumber", serialNumber)
    newDevice.initialize()

    log.trace("addEspressoMachine (complete)")
}

void removeEspressoMachine(ChildDeviceWrapper device) {
    log.trace("removeEspressoMachine")

    deleteChildDevice(device.getDeviceNetworkId())

    log.trace("removeEspressoMachine (complete)")
}

void refreshAccessToken() {
    log.trace("refreshAccessToken")

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    //if the access token is still valid, do nothing
    if (state.accessTokenExpires != null) {
        LocalDateTime accessTokenExpires = LocalDateTime.parse(state.accessTokenExpires.substring(10), formatter)
        
        if (LocalDateTime.now().isBefore(accessTokenExpires)) {
            //token still valid
            log.trace("refreshAccessToken (returning because access token is valid)")
            return
        }
    }

    if (settings?.apiHost == null) {
        throw new Exception("apiHost is required to be defined in settings")
    }

    if (settings?.customerEndpoint == null) {
        throw new Exception("customerEndpoint is required to be defined in settings")
    }

    if (settings?.username == null) {
        throw new Exception("Username is required")
    }

    if (state.refreshToken == null) {
        throw new Exception("refreshToken is missing from the state, please login again")
    }

    try {
        refreshAccessToken(settings?.apiHost, settings?.customerEndpoint, settings?.username, state.refreshToken) 
        {   accessToken, accessTokenExpires, newRefreshToken ->
                log.trace("refreshAccessToken - refreshAccessToken - closure")

                state.accessToken = accessToken
                state.accessTokenExpires = "DateTime: " + accessTokenExpires.format(formatter)
                state.refreshToken = newRefreshToken

                log.trace("refreshAccessToken - refreshAccessToken - closure (complete)")
        }
    }
    catch (Exception ex) {
        app.removeSetting("refreshToken")
        app.removeSetting("accessTokenExpires")
        throw ex
    }

    log.trace("refreshAccessToken (complete)")
}

/* Device Commands */

String getApiHost() {
    log.trace("getApiHost (immediate return)")

    return settings?.apiHost;
}

String getAccessToken() {
    log.trace("getAccessToken")

    refreshAccessToken()

    log.trace("getAccessToken (returning)")

    return state.accessToken
}

String getStreamingEndpoint() {

    log.trace("getStreamingEndpoint")

    if (settings?.apiHost == null) {
        throw new Exception("apiHost was not found in settings")
    }

    if (settings?.streamingEndpoint == null) {
        throw new Exception("streamingEndpoint was not found in settings")
    }

    log.trace("getStreamingEndpoint (returning)")

    return getEndpoint("STREAMING", ["apiHost" : settings?.apiHost, "streamingEndpoint" : settings?.streamingEndpoint])
}

void getMachineDashboard(String serialNumber, Closure handler) {
    log.trace("getMachineDashboard")

    if (settings?.apiHost == null) {
        throw new Exception("apiHost was not found in settings")
    }

    if (settings?.customerEndpoint == null) {
        throw new Exception("customerEndpoint was not found in settings")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber Number is required")
    }

    refreshAccessToken()

    if (state.accessToken == null) {
        throw new Exception("There is no access token stored in state, please login again")
    }

    getMachineDashboard(settings?.apiHost, settings?.customerEndpoint, serialNumber, state.accessToken) {
        dashboard ->
            handler(dashboard)
    }

    log.trace("getMachineDashboard (complete)")

}

void getMachineConfig(String serialNumber, Closure handler) {
    log.trace("getMachineConfig")

    if (settings?.apiHost == null) {
        throw new Exception("apiHost was not found in settings")
    }

    if (settings?.customerEndpoint == null) {
        throw new Exception("customerEndpoint was not found in settings")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber Number is required")
    }

    refreshAccessToken()

    if (state.accessToken == null) {
        throw new Exception("There is no access token stored in state, please login again")
    }

    getMachineConfig(settings?.apiHost, settings?.customerEndpoint, serialNumber, state.accessToken) {
        settings ->
            handler(settings)
    }

    log.trace("getMachineConfig (complete)")

}

void setMachinePower(String serialNumber, Boolean powerOn) {
    log.trace("setMachinePower")

    if (settings?.apiHost == null) {
        throw new Exception("apiHost was not found in settings")
    }

    if (settings?.customerEndpoint == null) {
        throw new Exception("customerEndpoint was not found in settings")
    }

    if (serialNumber == null) {
        throw new Exception("serialNumber Number is required")
    }

    if (powerOn == null) {
        throw new Exception("powerOn is required")
    }

    refreshAccessToken()

    if (state.accessToken == null) {
        throw new Exception("There is no access token stored in state, please login again")
    }

    Map<String, Object> commandParameters = [
        "mode" : powerOn ? getConstant("POWER_STATE_ACTIVE") : getConstant("POWER_STATE_STANDBY")
    ]

    executeCommand(settings.apiHost, settings.customerEndpoint, serialNumber, state.accessToken, getConstant("COMMAND_POWER_CHANGE_STATE"), commandParameters)

    log.trace("setMachinePower (complete)")
}
