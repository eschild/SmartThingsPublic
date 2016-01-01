/**
 *  Tweaked Circadian Daylight 
 *
 *  This SmartApp synchronizes your color changing lights with local perceived color  
 *     temperature of the sky throughout the day.  This gives your environment a more 
 *     natural feel, with cooler whites during the midday and warmer tints near twilight 
 *     and dawn.  
 * 
 *  In addition, the SmartApp can set your lights to a nice cool white at 1% in 
 *     "Sleep" mode, which is far brighter than starlight but won't reset your
 *     circadian rhythm or break down too much rhodopsin in your eyes.
 *
 *  Human circadian rhythms are heavily influenced by ambient light levels and 
 * 	hues.  Hormone production, brainwave activity, mood and wakefulness are 
 * 	just some of the cognitive functions tied to cyclical natural light.
 *	http://en.wikipedia.org/wiki/Zeitgeber
 * 
 *  Here's some further reading:
 * 
 * http://www.cambridgeincolour.com/tutorials/sunrise-sunset-calculator.htm
 * http://en.wikipedia.org/wiki/Color_temperature
 * 
 *  Oringal code by Kristopher Kubicki modified by Eric Schild for his specific use
 *
 *  Kristopher Kubicki Technical notes:  I had to make a lot of assumptions when writing this app
 *     *  I aligned the color space to CIE with white at D50.  I suspect "true"
 *		white for this application might actually be D65, but I will have
 *		to recalculate the color temperature if I move it.  
 *     *  There are no considerations for weather or altitude, but does use your 
 *		hub's zip code to calculate the sun position.    
 *     *  The app doesn't calculate a true "Blue Hour" -- it just sets the lights to
 *		2700K (warm white) until your hub goes into Night mode
 *  
 *  The latest original version of this file can be found at
 *     https://github.com/KristopherKubicki/smartapp-circadian-daylight/
 *   
 */

definition(
	name: "Tweaked Circadian Daylight",
	namespace: "eschild",
    author: "Kristopher Kubicki & Eric Schild",
	description: "Sync your color changing lights with natural daylight hues",
	category: "Green Living",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/MiscHacking/mindcontrol.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/MiscHacking/mindcontrol@2x.png"
)

preferences {
	section("Control these devices...") {
    	input "masterbulb", "capability.colorTemperature", title: "Which is the master bulb?", multiple:false, required: true
		input "ctbulbs", "capability.colorTemperature", title: "Which Temperature Changing Bulbs?", multiple:true, required: false
		input "dimmers", "capability.switchLevel", title: "Which Dimmers?", multiple:true, required: false
	}
    section("'Sleep' modes?") {
		input "smodes", "mode", title: "What are your Sleep modes?", multiple:true, required: false
	}
    section("'Override' mode?") {
		input "omode", "mode", title: "What is your Override mode?", required: true
        input(name: "otemp", type: "number", title: "What is your Override temp?", range: "2700..6200", required: true)
	}
    section("Enabled Dynamic Brightness?") { 
    	input "dbright","bool", title: "Yes or no?", required: false
    }
    section("Enabled Campfire instead of Moonlight?") { 
    	input "dcamp","bool", title: "Yes or no?", required: false
    }
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
    unschedule()
	initialize()
}

private def initialize() {
	log.debug("Initialized with settings: ${settings}")
    if(dimmers) { 
		subscribe(dimmers, "switch.on", modeHandler)
	}
    if(ctbulbs) { 
    	subscribe(masterbulb, "switch.on", modeHandler)
    }
	subscribe(location, "mode", modeHandler)
    
// revamped for sunset handling instead of motion events
    subscribe(location, "sunset", modeHandler)
    subscribe(location, "sunrise", modeHandler)
    subscribe(location, "sunsetTime", scheduleTurnOn)
    scheduleTurnOn()
}

def scheduleTurnOn(env=null) {
    //get the Date value for the string
    def sunsetTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", location.currentValue("sunsetTime"))
	def sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",location.currentValue("sunriseTime"))
    if(sunriseTime.time > sunsetTime.time) { 
    	sunriseTime = new Date(sunriseTime.time - (24 * 60 * 60 * 1000))
    }
    
    def runTime = new Date(now() + 60*15*1000)
    // arbitrary 20 step dimming.  Let's see if it works
    for (def i = 0; i <40; i++) {
        def long uts = sunriseTime.time + (i * ((sunsetTime.time - sunriseTime.time) / 40))
        def timeBeforeSunset = new Date(uts)
        if(timeBeforeSunset.time > now()) {
    		runTime = timeBeforeSunset
            i = 41
        }
    }

    def sdf = new java.text.SimpleDateFormat("EEE MMM d HH:mm:ss z")
    sdf.setTimeZone(location.timeZone.getLastRuleInstance())
    if(state.nextTimer){
    	log.info "nextTimer is set for ${sdf.format(state.nextTimer)}"
    }
        
    if(state.nextTimer != runTime.time) {
    	state.nextTimer = runTime.time
    	log.debug "Scheduling next step at: ${sdf.format(runTime)} (sunset is ${sdf.format(sunsetTime)}) :: ${state.nextTimer}"
    	runOnce(runTime, modeHandler)
   }
}

// wait for something to happen
def modeHandler(evt) {
    def ctb = getColorTempBright()
    for(dimmer in dimmers) {
       	if(dimmer.currentValue("switch") == "on" && dimmer.currentValue("level") != ctb.Bright) {     
    		dimmer.setLevel(ctb.Bright)
		}
	}
	for(ctbulb in ctbulbs) {
		if(ctbulb.currentValue("switch") == "on") { 
       		if(ctbulb.currentValue("level") != ctb.Bright) { 
           		log.debug "Changing bulb $ctbulb from ${ctbulb.currentValue("level")} to ${ctb.Bright}"
       			ctbulb.setLevel(ctb.Bright)
           	}
	          	log.debug "Setting $ctbulb temp to ${ctb.Temp}"
 				ctbulb.setColorTemperature(ctb.Temp)
       	}
	}
    scheduleTurnOn()
}

def getColorTempBright() {
	def after = getSunriseAndSunset()
	def midDay = after.sunrise.time + ((after.sunset.time - after.sunrise.time) / 2)
	def currentTime = now()
    def brightness = 1
	def int colorTemp = 2700    
	if(currentTime < after.sunrise.time) {
		colorTemp = 2700
	}
	else if(currentTime > after.sunset.time) { 
		colorTemp = 2700
	}
	else {
		if(currentTime < midDay) { 
			colorTemp = 2700 + ((currentTime - after.sunrise.time) / (midDay - after.sunrise.time) * 3300)
            brightness = ((currentTime - after.sunrise.time) / (midDay - after.sunrise.time))
		}
		else { 
			colorTemp = 6000 - ((currentTime - midDay) / (after.sunset.time - midDay) * 3300)
            brightness = 1 - ((currentTime - midDay) / (after.sunset.time - midDay))
		}
	}
	log.debug "Current location mode: ${location.mode}"
    if(dbright == false || location.mode == omode) { 
    	brightness = 1
    }
    if(location.mode == omode) { 
    // input validation seems to be useless so we will validate it every time
    	if(otemp < 2710){
        	colortemp = 2710
        } else if(otemp > 6200){
            colortemp = 6200
        } else {
 		   	colorTemp = otemp
        }
    }
    for (smode in smodes) {
		if(location.mode == smode) { 	
            if(dcamp == true) { 
            	colorTemp = 2700
            }
            else {
				colorTemp = 6000
            }
			brightness = 0.01
            last
       	}
	}

   	def ctb = [:]    
	ctb = [Temp: colorTemp, Bright: Math.round(brightness * 100) as Integer ?: 100]
	log.debug "Temp: ${ctb.Temp} Bright: ${ctb.Bright}"
    return ctb
}
