# hubitat-lamarzocco-driver
La Marzocco drivers for Hubitat

# Change Log
 *  1/11/2024 V0.9.0-alpha Alpha version with support for Machine State (on/off), Espresso Boiler temperature, and Water Level
 *  4/11/2024 V0.9.1-alpha Alpha fixed bug in access token refresh handling
 *  31/7/2025 v2.0.0-alpha Alpha version with support for latest gateway firmware (v5.2.7)

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

## Upgrade Instructions
1. Remove / Uninstall the existing device and app
2. Overwrite the existing driver, library and app code with the latest version
3. Follow the remaining setup instructions from steps 4-7

## Supported commands
Currently, only *ON* and *OFF* are supported at present. I will add more in time, especially if requested.

## Supported attributes
Only *espresso boiler status*, *espresso boiler target temperature*, *steam boiler status* and *water level* are currently supported at present. I will add more in time, especially if requested.

## HomeKit support
I've tested the *on/off* commands with HomeKit.

## Acknowledgements
This work is heavily adapted from Rob Coleman's work on [La Marzocco Home Assistant Integration](https://github.com/rccoleman/lamarzocco) and Josef Zweck's work on [La Marzocco Home Assistant Integration (Gateway v3)](https://github.com/zweckj/lamarzocco) & [pylamarzocco](https://github.com/zweckj/pylamarzocco).  Without their well documented work, this code would not be possible

## How to report bugs
If you encounter any bugs, please raise a new Issue in [Github](https://github.com/derekchoate/hubitat-lamarzocco-driver/issues)