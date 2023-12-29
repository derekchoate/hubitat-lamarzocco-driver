def version() {"0.0.1"}

@Field static final Boolean debug = true
enum LogLevel {
    debug,
    warning,
    error
}

metadata {
    definition (name: "La Marzocco Home Espresso Machine", namespace: "derekchoate", author: "Derek Choate", importUrl: "https://raw.githubusercontent.com/derekchoate/hubitat-lamarzocco-driver/release/latest/lm-espresso-machine.groovy" ) {
        capability "RelaySwitch"
        capability "TemperatureMeasurement"
        capability "WaterSensor"

        attribute "switch", "enum", ["off", "on"]
        attribute "temperature", "number"
        attribute "water", "enum", ["wet", "dry"]

        preferences {
            input "hostname", "String", "IP Address or Hostname"
            input "communicationKey", "String", "Communication Key"
        }
    }
}

/* START: driver lifecycle event handlers */

void installed() {
   log.debug "installed()"
}

void updated() {
   log.debug "updated()"

   //TODO: initialise connection
}

/* END: driver lifecycle event handlers */

/* START: Commands */

void on() {

    //TODO:  send "on" command

    // With a real device, you would normally send a Z-Wave/Zigbee/etc. command
    // to the device here.  For a virtual device, we are simply generating an
    // event to make the "switch" attribute "on". With a real device, you would
    // typically wait to hear back from the device in parse() before doing so.
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
}

void off() {
    //TODO:  send "off" command

    // Same notes as for on(), above, apply here...
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
}

/* END: Commands */

/* START: Communication */

void sendCommand() {
    //TODO: send command to device
}

void parse(String message) {
    log.debug "parse(\"${message}\")"
}

void socketStatus(String message) {
    log.debug "parse(\"${message}\")"
}

/* END: Communication */

/* START: Utility Methods */
void log(LogLevel logLevel, String message) {
    if (logLevel !== LogLevel.debug || debug) {
        log.debug(message)
    }
}
/* END: Utility Methods */