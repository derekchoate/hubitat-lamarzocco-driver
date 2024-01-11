# hubitat-lamarzocco-driver
La Marzocco drivers for Hubitat

## Installation Instructions
1. Copy the the lmCommon library code (lm-espresso-machine-library.groovy) into the "Libraries Code" section on HE
2. Copy the La Marzocco Home Espresso Machine driver code (lm-espresso-machine.groovy) into the "Drivers Code" section on HE
3. Copy the La Marzocco Home Espresso Machine Setup App (lm-espresso-machine-app.groovy) into the "App Code" section on HE
4. Add the La Marzocco Home Espresso Machine Setup App to HE by
- navigating to the "Apps" section
- clicking on the "+ Add User App" button on the top right
- then selecting "Marzocco Home Espresso Machine Setup App"
5. Authorize the connection by entering your username and password into the "Authorize Connection" section. 
- If you do do not have these details, register or reset your password using the La Marzocco App
6. Select the Machine(s) to install and hit 'Next'
7. Navigate to the Espresso Machine in the device section and complete final configuration if desired (set room name, etc)

## Supported commands
Currently, only *ON* and *OFF* are supported at present. I will add more in time, especially if requested.

## Supported attributes
Only *espresso boiler temperature* and *water level* are currently supported at present. I will add more in time, especially if requested.

## HomeKit support
I've tested the *on/off* commands with HomeKit, as well as the *temperature guage* for the espresso boiler, unfortunately I could not get the *water level* sensor to work.

## Acknowledgements
This work is heavily adapted from Rob Coleman's work on [La Marzocco Home Assistant Integration](https://github.com/rccoleman/lamarzocco) and Josef Zweck's work on [La Marzocco Home Assistant Integration (Gateway v3)](https://github.com/zweckj/lamarzocco).  Without their well documented work, this code would not be possible