/**
 * 	Color Coordinator 
 *  Version 1.0.0 - 7/4/15
 *  By Michael Struck
 *  Version 1.2 - 1/1/16
 *  By Eric Schild
 *
 *  1.0.0 - Initial release
 *  1.1 - Added support for Temperature changing Bulbs
 *  1.2 - Added optional lag support
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
definition(
	name: "Color Coordinator",
	namespace: "eschild",
	author: "Michael Struck",
	description: "Ties multiple colored lights to one specific light's settings",
	category: "Convenience",
	iconUrl: "https://raw.githubusercontent.com/MichaelStruck/SmartThings/master/Other-SmartApps/ColorCoordinator/CC.png",
	iconX2Url: "https://raw.githubusercontent.com/MichaelStruck/SmartThings/master/Other-SmartApps/ColorCoordinator/CC@2x.png"
)

preferences {
	page(name: "ChooseTypePage", title: "", nextPage: "mainPage", install: false, uninstall: true){
    	section() {
    		input("bulbType", "enum", options: ["colorControl":"Color Bulb","colorTemperature":"Temperature Bulb"], title:"Select bulb type")
        }
        section() {
    		input("enableresync", "boolean", default: false, title:"Enable resync function?")
        }
        section([mobileOnly:true], "") {
			href "pageAbout", title: "About ${textAppName()}", description: "Tap to get application version, license and instructions"
        }
    }
	page name: "mainPage"
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
		section("Master Light") {
			input "master", "capability.$bulbType", title: "Colored Light"
		}
		section("Lights that follow the master settings") {
			input "slaves", "capability.$bulbType", title: "Colored Lights",  multiple: true, required: false
		}
    	section([mobileOnly:true], "Options") {
			label(title: "Assign a name", required: false)
        }
	}
}

page(name: "pageAbout", title: "About ${textAppName()}") {
	section {
    	paragraph "${textVersion()}\n${textCopyright()}\n\n${textLicense()}\n"
	}
	section("Instructions") {
		paragraph textHelp()
	}
}

def installed() {   
	init() 
}

def updated(){
	unsubscribe()
    init()
}

def init() {
	subscribe(master, "switch", onOffHandler)
    subscribe(master, "colorTemperature", tempHandler)
	subscribe(master, "level", colorHandler)
    if(master.hasCommand("setColor")) {
    	subscribe(master, "hue", colorHandler)
    	subscribe(master, "saturation", colorHandler)
	}
}
//-----------------------------------
def onOffHandler(evt){
	if (master.currentValue("switch") == "on"){
    	slaves?.on()
    }
    else {
		slaves?.off()  
    }
    if(enableresync){
    	runIn(10, resyncHandler)
    }
}

def colorHandler(evt) {
   	def dimLevel = master.currentValue("level")
    if(master.hasCommand("setColor")) {
    	def hueLevel = master.currentValue("hue")
    	def saturationLevel = master.currentValue("saturation")
		def newValue = [hue: hueLevel, saturation: saturationLevel, level: dimLevel as Integer]
    	slaves?.setColor(newValue)
    } else {
    slaves?.setLevel(dimLevel)
    }
    if(enableresync){
    	runIn(10, resyncHandler)
    }
}

def tempHandler(evt){
    if (evt.value != "--") {
    	def tempLevel = master.currentValue("colorTemperature")
    	slaves?.setColorTemperature(tempLevel)
        if(enableresync){
    		runIn(10, resyncHandler)
    	}
    }
}

//added to handle a little delay in the network
def resyncHandler(evt){
	if (master.currentValue("switch") == "on"){
    	slaves?.on()
        def tempLevel = master.currentValue("colorTemperature")
    	slaves?.setColorTemperature(tempLevel)
		def dimLevel = master.currentValue("level")
        if(master.hasCommand("setColor")) {
    		def hueLevel = master.currentValue("hue")
    		def saturationLevel = master.currentValue("saturation")
			def newValue = [hue: hueLevel, saturation: saturationLevel, level: dimLevel as Integer]
    		slaves?.setColor(newValue)
    	} else {
    		slaves?.setLevel(dimLevel)
        }
    } else {
		slaves?.off()  
    }
}

//Version/Copyright/Information/Help

private def textAppName() {
	def text = "Color Coordinator"
}	

private def textVersion() {
    def text = "Version 1.2 (1/1/2016)"
}

private def textCopyright() {
    def text = "Copyright © 2015 Michael Struck.  Additional Copyright © 2016 Eric Schild."
}

private def textLicense() {
    def text =
		"Licensed under the Apache License, Version 2.0 (the 'License'); "+
		"you may not use this file except in compliance with the License. "+
		"You may obtain a copy of the License at"+
		"\n\n"+
		"    http://www.apache.org/licenses/LICENSE-2.0"+
		"\n\n"+
		"Unless required by applicable law or agreed to in writing, software "+
		"distributed under the License is distributed on an 'AS IS' BASIS, "+
		"WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. "+
		"See the License for the specific language governing permissions and "+
		"limitations under the License."
}

private def textHelp() {
	def text =
    	"This application will allow you to control the settings of multiple colored lights with one control. " +
        "Simply choose the type of your master light, set the master control light, and then choose the lights " + 
        "that will follow the settings of the master, including on/off conditions, level and color temperature. " + 
        "It will also set hue and saturation if supported by bulb choice."
}