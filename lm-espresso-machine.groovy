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
 */
#include derekchoate.lmCommon
import groovy.transform.Field
import groovy.time.TimeCategory
import com.hubitat.app.ChildDeviceWrapper
import java.time.*
import java.time.format.*
import groovy.json.*

def version() {"0.9.0-alpha"}

@Field final Boolean debug = true

metadata {
    definition (name: "La Marzocco Home Espresso Machine", namespace: "derekchoate", author: "Derek Choate", importUrl: "https://raw.githubusercontent.com/derekchoate/hubitat-lamarzocco-driver/release/latest/lm-espresso-machine.groovy" ) {
        capability "Initialize"
        capability "Polling"
        capability "Switch"
        capability "TemperatureMeasurement"

        attribute "switch", "enum", ["off", "on"]
        attribute "waterLevel", "enum", ["full", "empty"]
        attribute "temperature", "number"
        attribute "firmwareVersion", "String"
        attribute "gatewayFirmware", "String"
        attribute "manufacturer", "String"
        attribute "model", "String"

        preferences {
            input (name: "serialNumber", type: "text", title: "Machine Serial Number")
        }

        command "reset"
        command "disconnect"
    }
}

/* START: driver lifecycle event handlers */

void installed() {

}

void updated() {
    initialize()
}

void initialize() {
    disconnect()
    refreshAll()
    initializeStreaming()
}

void uninstall() {
    disconnect()
}

/* END: driver lifecycle event handlers */

/* START: Commands */

void on() {
    if (settings?.serialNumber == null) {
        throw new Exception("Serial number not set")
    }

    parent.setMachineStatus(settings?.serialNumber, getConstant("MACHINE_STATUS_ON"))
}

void off() {
    if (settings?.serialNumber == null) {
        throw new Exception("Serial number not set")
    }

    parent.setMachineStatus(settings?.serialNumber, getConstant("MACHINE_STATUS_OFF"))
}

void poll() {
    refreshConfig()
}

void reset() {
    interfaces.webSocket.close()
    state.communicationKey = null
    state.localIpAddress = null
}

void disconnect() {
    interfaces.webSocket.close()
}

/* END: Commands */

/* START: Communication */

void initializeStreaming() {
    //log.trace("initializeStreaming")
    interfaces.webSocket.close()

    if (state.localIpAddress == null) {
        throw new Exception("Local IP Address not set")
    }

    if (state.communicationKey == null) {
        throw new Exception("CommunicationKey not set")
    }

    String localEndpoint = getEndpoint("STREAMING", ["localIpAddress" : state.localIpAddress])

    interfaces.webSocket.connect(localEndpoint, headers: ["Authorization":"Bearer ${state.communicationKey}", "Accept": "application/json"])

}

void parse(String message) {
    //log.trace("parse")
    //log.info("parse(\"${message}\")")

    JsonSlurper jsonSlurper = new JsonSlurper()
    def messageData = jsonSlurper.parseText(message)

    if (messageData instanceof Map && messageData.containsKey("MachineConfiguration")) { 
        handleConfigUpdate(jsonSlurper.parseText(messageData["MachineConfiguration"]))
        return
    }
    if (messageData instanceof List && messageData.size() >= 1 && messageData[0] instanceof Map) {
        if (messageData[0].containsKey("CoffeeBoiler1UpdateTemperature")) {
            updateEspressBoilerTemp(messageData[0]["CoffeeBoiler1UpdateTemperature"])
        }
    }

}

void webSocketStatus(String status) {
    //log.trace("webSocketStatus")
    log.info("webSocketStatus(\"${status}\")")
}

void refreshAll() {
    //log.trace("refreshAll")
    refreshMachineDetails()
    refreshMachineIpAddress()
    refreshConfig()
}

void refreshMachineDetails() {
    //log.trace("refreshMachineDetails")
    if (settings?.serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    parent.refreshMachineDetails(settings?.serialNumber) 

    {communicationKey, modelName -> 
    
        state.communicationKey = communicationKey

        updateModel(modelName)
    }
}

void refreshMachineIpAddress() {
    //log.trace("refreshMachineIpAddress")
    if (settings?.serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    parent.refreshMachineIpAddress(settings?.serialNumber)
    {ipAddress -> 
        state.localIpAddress = ipAddress
    }
}


void refreshStatus() {
    //log.trace("refreshStatus")
    if (settings?.serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    parent.refreshStatus(settings?.serialNumber)
    {machineStatus, tankLevel ->
        updateMachineState(machineStatus)
        updateWaterLevel(tankLevel)
    }
}

void refreshConfig() {
    //log.trace("refreshConfig")
    if (state.localIpAddress == null || state.communicationKey == null) {
        throw new Exception("Unable to refresh config becuase the local IP is not known, or the communication key is not known")
    }

    getMachineConfig(state.localIpAddress, state.communicationKey)
    {config -> 
        handleConfigUpdate(config)
    }
}

void handleConfigUpdate(Map<String, Object> config) {
    //log.trace("handleConfigUpdate")
    Map<String, Object> espressoBoilerConfig = config.boilers.find({it.id == "CoffeeBoiler1"})
    Map<String, Object> steamBoilerConfig = config.boilers.find({it.id == "SteamBoiler"})
    Map<String, Object> machineFirmware = config.firmwareVersions.find({it.name == "machine_firmware"})
    Map<String, Object> gatewayFirmware = config.firmwareVersions.find({it.name == "gateway_firmware"})

    // log.info("Machine status is ${config.machineMode}")
    // log.info("Espresso boiler isEnabled = ${espressoBoilerConfig.isEnabled}")
    // log.info("Espresso boiler temperature = ${espressoBoilerConfig.current}")
    // log.info("Steam boiler isEnabled = ${steamBoilerConfig.isEnabled}")
    // log.info("Tank status = ${config.tankStatus}")
    // log.info("Machine firmware version = ${machineFirmware.fw_version}")
    // log.info("Gateway firmware version = ${gatewayFirmware.fw_version}")

    updateFirmware(machineFirmware.fw_version, gatewayFirmware.fw_version)
    updateMachineState(config.machineMode)
    updateWaterLevel(config.tankStatus)
    updateEspressBoilerTemp(espressoBoilerConfig.current)
    updateSteamBoilerState(steamBoilerConfig.isEnabled)
}

void updateModel(String model) {
    //log.trace("updateModel")
    sendEvent(name: "manufacturer", value: "La Marzocco")
    sendEvent(name: "model", value: model)
}

void updateFirmware(String machineVersion, String gatewayVersion) {
    //log.trace("updateFirmware")
    sendEvent(name: "firmwareVersion", value: machineVersion)
    sendEvent(name: "gatewayFirmware", value: gatewayVersion)
}

void updateMachineState (String state) {
    //log.trace("updateMachineState")
    if (getConstant("MACHINE_STATUS_ON").equalsIgnoreCase(state)) {
        sendEvent(name: "switch", value: "on") 
    }
    else {
        sendEvent(name: "switch", value: "off")
    }
}

void updateEspressBoilerTemp (Number temp) {
    //log.trace("updateEspressBoilerTemp")
    sendEvent(name: "temperature", value: temp, unit: "C", descriptionText: "${device.displayName} temperature is ${currentTemp}")
}

void updateSteamBoilerState (Boolean active) {
    //log.trace("updateSteamBoilerState")
    // ChildDeviceWrapper steamBoiler = getSteamBoiler()
    // if (steamBoiler == null) {
    //     steamBoiler = createSteamBoiler()
    // }

    // if (steamBoiler == null) {
    //     log.warn("Cannot update steam boiler status because the device was not found")
    //     return
    // }

    // steamBoiler.handlePowerUpdate(active)
}

void updateWaterLevel (Boolean hasWater) {
    //log.trace("updateWaterLevel")
    if (hasWater == true) {
        sendEvent(name: "waterLevel", value: "full", descriptionText: "${device.displayName} has water in the tank")    
    }
    else {
        sendEvent(name: "waterLevel", value: "empty", descriptionText: "${device.displayName} needs to be filled")
    }
}

// ChildDeviceWrapper createSteamBoiler() {
//     //log.trace("createSteamBoiler")
//     return addChildDevice('derekchoate', "La Marzocco Home Espresso Machine Steam Boiler", "${device.deviceNetworkId}-steam-boiler", [label: "Steam Boiler", isComponent: true])
// }

// ChildDeviceWrapper getSteamBoiler() {
//     //log.trace("getSteamBoiler")
//     return getChildDevice("${device.deviceNetworkId}-steam-boiler")
// }

/* END: Utility Methods */