/**
 *
 *  Qubino Smart Meter
 *
 *	github: cattivik66
 *	Date: 2017-04-03
 *	Copyright cattivik66
 *
 *  Version 0.1
 *
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

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
	//if (cmd) {
    	log.debug "Creating event $cmd"
		result = createEvent(zwaveEvent(cmd))
	//}
	log.debug "Parsed '$description' to $result"
	return result
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
            
            if (state.powerLow == null || state.powerLow > Math.round(cmd.scaledMeterValue)) // state.powerLow == null || 
            {
            	log.debug "New powerLow value"
            	state.powerLow = cmd.scaledMeterValue
                sendEvent(name: "powerLow", value: Math.round(cmd.scaledMeterValue), unit: "W")
            }

        	return createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
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


def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	log.debug "crc16encapv1.Crc16Encap"
	def versions = [0x20: 1, 0x25: 1, 0x02: 1, 0x50: 1]
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

private encap(cmd, endpoint) {
	if (endpoint) {
		zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd)
	} else {
		cmd
	}
}

def refresh() {
	delayBetween([
		zwave.meterV2.meterGet(scale: 0).format(),
        //zwave.meterV2.meterGet(scale: 1).format(), //
		zwave.meterV2.meterGet(scale: 2).format(),
        //zwave.meterV2.meterGet(scale: 3).format(), //
        zwave.meterV2.meterGet(scale: 4).format(),
        //zwave.meterV2.meterGet(scale: 5).format(), //
        zwave.meterV2.meterGet(scale: 6).format(),
        
        //zwave.meterV2.meterGet(scale: 7).format(),
        //zwave.meterV2.meterGet(scale: 8).format(),
	])
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "$device.displayName: $cmd"
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