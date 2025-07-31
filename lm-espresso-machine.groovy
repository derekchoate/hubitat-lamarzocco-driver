/**
 *  La Marzocco Espresso Machine driver for Hubitat
 *
 *  Copyright 2025 Derek Choate
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
#include derekchoate.lmCommon
import groovy.transform.Field
import groovy.time.TimeCategory
import com.hubitat.app.ChildDeviceWrapper
import java.time.*
import java.time.format.*
import java.util.UUID
import groovy.json.*

def version() {"2.0.0-alpha"}

@Field final Boolean debug = true

metadata {
    definition (name: "La Marzocco Home Espresso Machine", namespace: "derekchoate", author: "Derek Choate", importUrl: "https://raw.githubusercontent.com/derekchoate/hubitat-lamarzocco-driver/release/latest/lm-espresso-machine.groovy" ) {
        capability "Initialize"
        capability "Switch"

        attribute "switch", "enum", ["off", "on"]
        attribute "online", "enum", ["disconnected", "connected"]
        attribute "waterLevel", "enum", ["full", "empty"]
        attribute "coffeeBoilerStatus", "enum", ["StandBy", "HeatingUp", "Ready", "NoWater"]
        attribute "coffeeBoilerTargetTemperature", "decimal"
        attribute "steamBoilerStatus", "enum", ["Off", "StandBy", "HeatingUp", "Ready", "NoWater"]
        attribute "steamBoilerEnabled", "enum", ["off", "on"]
        attribute "firmwareVersion", "String"
        attribute "gatewayFirmware", "String"
        attribute "manufacturer", "String"
        attribute "model", "String"
        attribute "name", "String"
        attribute "lastUpdated", "String"

        preferences {
            input (name: "serialNumber", type: "text", title: "Machine Serial Number")
        }

        command "reset"
        command "disconnect"
    }
}

/* START: driver lifecycle event handlers */

void installed() {
    //log.trace("installed (stated / completed)")
}

void updated() {
    //log.trace("updated")
    initialize()
    //log.trace("updated (completed)")
}

void initialize() {
    //log.trace("initialize")

    disconnect()
    refreshAll()
    initializeStreaming()

    //log.trace("initialize (completed)")
}

void uninstall() {
    //log.trace("uninstall")

    disconnect()

    //log.trace("uninstall (completed)")
}

/* END: driver lifecycle event handlers */

/* START: Commands */

void on() {
    //log.trace("on")
    if (settings?.serialNumber == null) {
        throw new Exception("Serial number not set")
    }

    parent.setMachinePower(settings?.serialNumber, true)

    //log.trace("on (completed)")
}

void off() {
    //log.trace("off")

    if (settings?.serialNumber == null) {
        throw new Exception("Serial number not set")
    }

    parent.setMachinePower(settings?.serialNumber, false)

    //log.trace("off (completed)")
}

void reset() {
    //log.trace("reset")
    
    disconnect()

    sendEvent(name: "switch", value: "off", descriptionText: "value has been reset")
    sendEvent(name: "online", value: "disconnected", descriptionText: "value has been reset")
    sendEvent(name: "waterLevel", value: "full", descriptionText: "value has been reset")
    sendEvent(name: "coffeeBoilerStatus", value: "StandBy", descriptionText: "value has been reset")
    sendEvent(name: "coffeeBoilerTargetTemperature", value: 0.0, descriptionText: "value has been reset")
    sendEvent(name: "steamBoilerStatus", value: "Off", descriptionText: "value has been reset")
    sendEvent(name: "steamBoilerEnabled", value: "off", descriptionText: "value has been reset")
    sendEvent(name: "firmwareVersion", value: "", descriptionText: "value has been reset")
    sendEvent(name: "gatewayFirmware", value:  "", descriptionText: "value has been reset")
    sendEvent(name: "manufacturer", value:  "", descriptionText: "value has been reset")
    sendEvent(name: "model", value:  "", descriptionText: "value has been reset")
    sendEvent(name: "name", value:  "", descriptionText: "value has been reset")
    sendEvent(name: "lastUpdated", value:  "", descriptionText: "value has been reset")

    state.dashboardSubscriptionId = null

    initialize()

    //log.trace("reset (completed)")
}

void disconnect() {
    //log.trace("disconnect")

    unsubscribeFromDashboard()
    interfaces.webSocket.close()

    //log.trace("disconnect (completed)")
}

/* END: Commands */

/* START: Communication */

void initializeStreaming() {
    //log.trace("initializeStreaming")

    disconnect()

    if (settings?.serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    String streamingEndpoint = parent.getStreamingEndpoint()

    interfaces.webSocket.connect(streamingEndpoint)

    //log.trace("initializeStreaming (completed)")
}

void webSocketStatus(String status) {
    //log.trace("webSocketStatus")

    if (status.startsWith("status: open")) {
        sendConnectMessage()
    }

    //log.trace("webSocketStatus (completed)")
}

void sendConnectMessage() {
    //log.trace("sendConnectMessage")

    String accessToken = parent.getAccessToken();

    if (accessToken == null) {
        throw new Exception("Access token cannot be null, please re-login")
    }

    Map<String, String> headers = [
        "host" : parent.getApiHost(),
        "accept-version" : "1.2,1.1,1.0",
        "heart-beat" : "0,0",
        "Authorization" : "Bearer ${accessToken}"
    ]
    String message = formatStompMessage("CONNECT", headers, null)

    interfaces.webSocket.sendMessage(message)

    //log.trace("sendConnectMessage (completed)")
}

void subscribeToDashboard() {
    //log.trace("subscribeToDashboard")

    state.dashboardSubscriptionId = UUID.randomUUID().toString()

    Map<String, String> headers = [
        "destination": "/ws/sn/${settings?.serialNumber}/dashboard",
        "ack": "auto",
        "id": UUID.randomUUID().toString(),
        "content-length": "0",
    ]

    String message = formatStompMessage("SUBSCRIBE", headers, null)

    interfaces.webSocket.sendMessage(message)

    //log.trace("subscribeToDashboard (completed)")
}

void unsubscribeFromDashboard() {
    //log.trace("unsubscribeFromDashboard")

    if (state.dashboardSubscriptionId == null) {
        //log.info("Cannot unsubscribe from dashboard because the subscription id does not exist in the settings")
        return
    }

    Map<String, String> headers = [
        "id" : state.dashboardSubscriptionId
    ]

    String message = formatStompMessage("UNSUBSCRIBE", headers, null)

    interfaces.webSocket.sendMessage(message)

    //log.trace("unsubscribeFromDashboard (completed)")
}

void parse(String message) {
    //log.trace("parse")

    parseStompMessage(message) {
        messageType, headers, body -> 
            if (messageType == "CONNECTED") {
                subscribeToDashboard()
            }
            else if (messageType == "ERROR") {
                log.error("received an error from the API:\n${message}")
            }
            else if (messageType != "MESSAGE") {
                log.warn("received an unsupported message from the API:\n${message}")
            }
            else {
                handleDashboardMessage(body)
            }
    }

    //log.trace("parse (completed)")
}

void handleDashboardMessage(String message) {
    //log.trace("handleDashboardMessage")

    JsonSlurper jsonSlurper = new JsonSlurper()
    def messageData = jsonSlurper.parseText(message)

    if (!(messageData instanceof Map)) { 
        //log.trace("handleDashboardMessage (returning early because messageData is not a map)")
        log.warn("Unsupported message received from API:\n${message}")
        return
    }
    
    handleDashboard(messageData)

    //log.trace("handleDashboardMessage (completed)")
}

void handleDashboard(Map<String, Object> dashboard) {
    //log.trace("handleDashboard")

    if (dashboard?.widgets == null || !(dashboard.widgets instanceof List)) {
        //log.trace("handleDashboard (returning early because input is unexpected)")
        return
    }

    try {
        handleConfigUpdate(dashboard)
        handleMachineState(dashboard.widgets.find{it.code == "CMMachineStatus"})
        handleCoffeeBoiler(dashboard.widgets.find{it.code == "CMCoffeeBoiler"})
        handleSteamBoiler(dashboard.widgets.find{it.code == "CMSteamBoilerTemperature"})

        updateLastUpdated()
    }
    catch (Exception ex) {
        log.error("handleDashboard - Unable to update status because of an exception")
        log.error(ex)
    }

    //log.trace("handleDashboard (completed)")
}


void handleMachineState(Map<String, Object> widget) {
    //log.trace("handleMachineState")

    if (widget?.output == null) {
        //log.trace("handleMachineState (returning because input is null)")
        return
    }
    updateMachineState(widget.output.status)

    //log.trace("handleMachineState (completed)")
}

void handleCoffeeBoiler(Map<String, Object> widget) {
    //log.trace("handleCoffeeBoiler")

    if (widget?.output == null) {
        //log.trace("handleCoffeeBoiler (returning because input is null)")
        return
    }

    updateWaterLevel(widget.output.status != "NoWater");
    updateEspressBoilerStatus(widget.output.status, widget.output.targetTemperature)

    //log.trace("handleCoffeeBoiler (completed)")
}

void handleSteamBoiler(Map<String, Object> widget) {
    //log.trace("handleSteamBoiler")

    if (widget?.output == null) {
        //log.trace("handleSteamBoiler (returning because input is null)")
        return
    }

    updateSteamBoilerStatus(widget.output.status, widget.output.enabled)

    //log.trace("handleSteamBoiler (completed)")
}

void refreshAll() {
    //log.trace("refreshAll")

    refreshDashboard()
    refreshConfig()

    //log.trace("refreshAll (completed)")
}

void refreshDashboard() {
    //log.trace("refreshDashboard")

    if (settings?.serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    parent.getMachineDashboard(settings?.serialNumber)
    {dashboard ->
        handleDashboard(dashboard)
    }

    //log.trace("refreshDashboard (completed)")
}

void refreshConfig() {
    //log.trace("refreshConfig")

    if (settings?.serialNumber == null) {
        throw new Exception("Device Serial Number is required")
    }

    parent.getMachineConfig(settings?.serialNumber)
    {config -> 
        handleConfigUpdate(config)
    }

    //log.trace("refreshConfig (completed)")
}

void handleConfigUpdate(Map<String, Object> config) {
    //log.trace("handleConfigUpdate")

    if (config == null) {
        //log.trace("handleConfigUpdate (returning early because config is null)")
        return
    }

    updateName(config.name)
    updateModel(config.modelName)
    updateConnected(config.connected)

    updateFirmware(config.actualFirmwares.find{it.type == "Machine"}, config.actualFirmwares.find{it.type == "Gateway"})

    //log.trace("handleConfigUpdate (completed)")
}

void updateLastUpdated() {
    //log.trace("updateLastUpdated")

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    sendEvent(name: "lastUpdated", value: LocalDateTime.now().format(formatter))

    //log.trace("updateLastUpdated (completed)")
}

void updateName(String name) {
    //log.trace("updateName")

    if (name == null || name.trim() == "") {
        //log.trace("updateName (returning early because input is null or empty)")
        return
    }

    sendEvent(name: "name", value: name)

    //log.trace("updateName (completed)")
}

void updateModel(String model) {
    //log.trace("updateModel")

    if (model == null || model.trim() == "") {
        //log.trace("updateModel (returning early because input is null or empty)")
        return
    }
    
    sendEvent(name: "manufacturer", value: "La Marzocco")
    sendEvent(name: "model", value: model)

    //log.trace("updateModel (completed)")
}

void updateFirmware(Map<String, Object> machineFirmware, Map<String, Object> gatewayFirmware) {
    //log.trace("updateFirmware")

    if (machineFirmware != null) {
        sendEvent(name: "firmwareVersion", value: machineFirmware?.buildVersion)
    }

    if (gatewayFirmware != null) {
        sendEvent(name: "gatewayFirmware", value: gatewayFirmware?.buildVersion)
    }

    //log.trace("updateFirmware (completed)")
}

void updateConnected(Boolean connected) {
    //log.trace("updateConnected")

    sendEvent(name: "online", value: connected ? "connected" : "disconnected")

    //log.trace("updateConnected (completed)")
}

void updateMachineState (String state) {
    //log.trace("updateMachineState")

    if ("PoweredOn".equalsIgnoreCase(state)) {
        sendEvent(name: "switch", value: "on") 
    }
    else {
        sendEvent(name: "switch", value: "off")
    }

    //log.trace("updateMachineState (completed)")
}

void updateEspressBoilerStatus (String status, Double targetTemp) {
    //log.trace("updateEspressBoilerStatus")
    
    sendEvent(name: "coffeeBoilerStatus", value: status)
    sendEvent(name: "coffeeBoilerTargetTemperature", value: targetTemp, unit: "C", descriptionText: "${device.displayName} target temperature is ${targetTemp}")

    //log.trace("updateEspressBoilerStatus (completed)")
}

void updateSteamBoilerStatus (String status, Boolean enabled) {
    //log.trace("updateSteamBoilerStatus")
    
    sendEvent(name: "steamBoilerStatus", value: status)
    sendEvent(name: "steamBoilerEnabled", value: enabled)

    //log.trace("updateSteamBoilerStatus (completed)")
}

void updateWaterLevel (Boolean hasWater) {
    //log.trace("updateWaterLevel")
    
    if (hasWater == true) {
        sendEvent(name: "waterLevel", value: "full", descriptionText: "${device.displayName} has water in the tank")    
    }
    else {
        sendEvent(name: "waterLevel", value: "empty", descriptionText: "${device.displayName} needs to be filled")
    }

    //log.trace("updateWaterLevel (completed)")
}

/* END: Utility Methods */