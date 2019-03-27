/**
 *
 *  Qubino Smart Meter
 *
 *	github: cattivik66
 *	First release date: 2017-04-03
 *  0.2 release date: 2019-03-27
 *	Copyright cattivik66
 *
 *  Version 0.2
 * 
 * - Updated the code to make it working with encapsulated data
 *
 *  Version 0.1
 *  - Initial release, currently cannot manage the two switches or configuration parameters
 *
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
 */
metadata {
	definition (name: "Qubino Smart Meter", namespace: "cattivik66", author: "cattivik66") {
    	capability "Actuator"
		capability "Energy Meter"
		capability "Power Meter"
		capability "Refresh"
		capability "Polling"
		capability "Sensor"
		capability "Switch"
        capability "Configuration"
        
        
        attribute "reactiveEnergy", "string"
        
        attribute "voltage", "string"
        attribute "powerFactor", "string"
        
        attribute "powerHigh", "string"
        attribute "powerLow", "string"
        
        attribute "switch1", "string"
		attribute "switch2", "string"

		command "reset"
        
        fingerprint mfr: "0159", prod:"0007", model: "0052", manufacturer: "Qubino"
		//fingerprint inClusters: "0x32"
	}

	// simulator metadata
	simulator {

}

	// tile definitions
	tiles {
		valueTile("power", "device.power", width: 2, height: 2) {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy") {
			state "default", label:'${currentValue} kWh'
		}
        valueTile("voltage", "device.voltage") {
			state "default", label:'${currentValue} V'
		}
        valueTile("powerFactor", "device.powerFactor") {
			state "default", label:'${currentValue} pf'
		}
        
        valueTile("powerHigh", "device.powerHigh") {
			state "default", label:'max ${currentValue} W'
		}
        valueTile("powerLow", "device.powerLow") {
			state "default", label:'min ${currentValue} W'
		}
        
        /*
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset kWh', action:"reset"
		}*/
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main (["power","energy", "voltage", "powerFactor", "powerHigh", "powerLow"])
		details(["power","energy", "voltage", "powerFactor", "powerHigh", "powerLow", "refresh"])
	}
}

def updated() {
	response(refresh())
}

private Map cmdVersions() {
    [0x5E: 1, 0x86: 1, 0x72: 1, 0x59: 1, 0x73: 1, 0x22: 1, 0x56: 1, 0x32: 3, 0x71: 1, 0x98: 1, 0x7A: 1, 0x25: 1, 0x5A: 1, 0x85: 2, 0x70: 2, 0x8E: 2, 0x60: 3, 0x75: 1, 0x5B: 1] //Fibaro Single Switch 2
}

def parse(String description) {
    log.debug "START Parsing: $description"
    def result = []
    log.debug "Parsing: $description"
    if (description.startsWith("Err 106")) {
        result = createEvent(
                descriptionText: "Failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.",
                eventType: "ALERT",
                name: "secureInclusion",
                value: "failed",
                displayed: true,
        )
    } else if (description == "updated") {
        return null
    } else {
        def cmd = zwave.parse(description, cmdVersions())
        if (cmd) {
            log.debug "Parsed: $cmd"
            zwaveEvent(cmd)
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
        log.debug "MeterReport V1 , scale: $cmd.scale"
        switch(cmd.scale) {
    	case 0: //kWh
        	log.debug "meter is for KWh (case 0), value is $cmd.scaledMeterValue"
        	return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
            break;
		case 1: //kVAh
        	log.debug "meter is for kVAh (case 1)"
        	state.reactiveEnergy = cmd.scaledMeterValue
			return createEvent(name: "reactiveEnergy", value: cmd.scaledMeterValue, unit: "kVAh")
            break;
		case 2: //Watts
        	log.debug "meter is for Watts (case 2), and is $cmd.scaledMeterValue W"
            if (Math.round(cmd.scaledMeterValue) > 1)
            {
                //log.debug state.powerHigh
                if (state.powerHigh == null || state.powerHigh < Math.round(cmd.scaledMeterValue)) // state.powerHigh == null || 
                {
                    log.debug "New powerHigh value"
                    state.powerHigh = cmd.scaledMeterValue
                    sendEvent(name: "powerHigh", value: Math.round(cmd.scaledMeterValue), unit: "W")
                }

                if (state.powerLow == null || state.powerLow < 2 || state.powerLow > Math.round(cmd.scaledMeterValue)) // state.powerLow == null || 
                {
                    log.debug "New powerLow value"
                    state.powerLow = cmd.scaledMeterValue
                    sendEvent(name: "powerLow", value: Math.round(cmd.scaledMeterValue), unit: "W")
                }

                return createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
            }
            break;
		case 3: //pulses
        	log.debug "meter is for pulses (case 3)"
        	break;
        case 4: //Volts
        	log.debug "meter is for Volts (case 4)"
        	return createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
		case 5: //Amps
        	log.debug "meter is for Amps (case 5)"
        	break;
        case 6: //Power Factor
        	log.debug "meter is for Power Factor (case 6)"
			return createEvent(name: "powerFactor", value: cmd.scaledMeterValue, unit: "pf")
        	break;
        default:
            log.debug "Scale: "
            log.debug cmd.scale
            log.debug "scaledMeterValue: "
            log.debug cmd.scaledMeterValue
        	break;
    }
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	log.debug "MeterReport V3 "
    switch(cmd.scale) {
    	case 0: //kWh
        	return createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
		case 1: //kVAh
        	state.reactiveEnergy = cmd.scaledMeterValue
			return createEvent(name: "reactiveEnergy", value: cmd.scaledMeterValue, unit: "kVAh")
		case 2: //Watts
        	//log.debug state.powerHigh
            if (state.powerHigh == null || state.powerHigh < Math.round(cmd.scaledMeterValue)) // state.powerHigh == null || 
            {
            	log.debug "New powerHigh value"
            	state.powerHigh = cmd.scaledMeterValue
                sendEvent(name: "powerHigh", value: Math.round(cmd.scaledMeterValue), unit: "W")
            }
            
            if (state.powerLow == null || state.powerLow < 2 || state.powerLow > Math.round(cmd.scaledMeterValue)) // state.powerLow == null || 
            {
            	log.debug "New powerLow value"
            	state.powerLow = cmd.scaledMeterValue
                sendEvent(name: "powerLow", value: Math.round(cmd.scaledMeterValue), unit: "W")
            }

        	//return createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
            sendEvent([name: "power", value: cmd.scaledMeterValue, unit: "W"]);
            break;
		case 3: //pulses
        	break;
        case 4: //Volts
        	return createEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
		case 5: //Amps
        	break;
        case 6: //Power Factor
			return createEvent(name: "powerFactor", value: cmd.scaledMeterValue, unit: "pf")
        	break;
        default:
            log.debug "Scale: "
            log.debug cmd.scale
            log.debug "scaledMeterValue: "
            log.debug cmd.scaledMeterValue
        	break;
    }
}



private secEncap(physicalgraph.zwave.Command cmd) {
    logging("encapsulating command using Secure Encapsulation, command: $cmd","info")
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(physicalgraph.zwave.Command cmd) {
    logging("encapsulating command using CRC16 Encapsulation, command: $cmd","info")
    zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

private multiEncap(physicalgraph.zwave.Command cmd, Integer ep) {
    logging("encapsulating command using MultiChannel Encapsulation, ep: $ep command: $cmd","info")
    zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:ep).encapsulate(cmd)
}

private encap(physicalgraph.zwave.Command cmd, Integer ep) {
    encap(multiEncap(cmd, ep))
}

private encap(List encapList) {
    encap(encapList[0], encapList[1])
}

private encap(Map encapMap) {
    encap(encapMap.cmd, encapMap.ep)
}

private encap(physicalgraph.zwave.Command cmd) {
    if (zwaveInfo.zw.contains("s")) {
        secEncap(cmd)
    } else if (zwaveInfo.cc.contains("56")){
        crcEncap(cmd)
    } else {
        logging("no encapsulation supported for command: $cmd","info")
        cmd.format()
    }
}

private encapSequence(cmds, Integer delay=250) {
    delayBetween(cmds.collect{ encap(it) }, delay)
}

private encapSequence(cmds, Integer delay, Integer ep) {
    delayBetween(cmds.collect{ encap(it, ep) }, delay)
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
    if (encapsulatedCommand) {
        logging("Parsed MultiChannelCmdEncap ${encapsulatedCommand}")
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    } else {
        logging("Unable to extract MultiChannel command from $cmd","warn")
    }
}


def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions())
    if (encapsulatedCommand) {
        logging("Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}")
        zwaveEvent(encapsulatedCommand)
    } else {
        logging("Unable to extract Secure command from $cmd","warn")
    }
}
def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	log.debug "crc16encapv1.Crc16Encap"
    def version = cmdVersions()[cmd.commandClass as Integer]
    def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
    def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
    if (encapsulatedCommand) {
        logging("Parsed Crc16Encap into: ${encapsulatedCommand}")
        zwaveEvent(encapsulatedCommand)
    } else {
        logging("Unable to extract CRC16 command from $cmd","warn")
    }
}



private command(physicalgraph.zwave.Command cmd) {
	if (state.sec) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=500) {
	delayBetween(commands.collect{ command(it) }, delay)
}

def refresh() {
	def request = [
    	zwave.meterV3.meterGet(scale: 0),	//kWh
		zwave.meterV3.meterGet(scale: 2),	//Wattage
		zwave.meterV3.meterGet(scale: 4),	//Volts
		zwave.meterV3.meterGet(scale: 6),	//Power Factor
	]
	commands(request)
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "EVENT DEBUG $device.displayName: $cmd"
	[:]
}

def poll() {
	refresh()
}

def reset() {
/*
delayBetween([
		zwave.meterV2.meterReset().format(),
		zwave.meterV2.meterGet(scale: 0).format()
	], 1000)
    */
}
