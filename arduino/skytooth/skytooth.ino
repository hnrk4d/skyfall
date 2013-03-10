// -*-C++-*-
/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/

#include <SoftwareSerial.h>
#include <Wire.h>
#include <BMP085.h>
#include <ledout.h>
#include "ADXL345.h"

#define LOG_SERIAL(X)

//BMP085 variables
BMP085 sDps = BMP085();      // Digital Pressure Sensor 
long sTemperature=0, sPressure=0, sStartPressure=0;
bool sFirstPressure=true;
bool sSecurityMode=true;
bool sFreefallDetected=false;

//Motor control - TB6612FNG
#define AIN1 4
#define PWMA 5
#define BIN1 7
#define PWMB 6
#define STBY 12

//Bluetooth module
#define TX 2
#define RX 3

//LED
#define LED 13
LedOut sLed(LED);

//General
#define CMDSIZE 20
char sCmd[CMDSIZE+1];
int sCmdPos=0;

//ADXL free fall detection
ADXL345 adxl;

//Modes
#define MODE_NONE 0
#define MODE_CLIMBING 1
#define MODE_FREE_FALL 2
#define MODE_PARACHUTE_1 3
#define MODE_PARACHUTE_2 4
#define MODE_PARACHUTE_3 5
#define MODE_DONE 6
#define MODE_TEST_BALLOON 7
#define MODE_TEST_PARACHUTE 8
#define MAX_MODE 8

int sPrevMode=MODE_NONE;
int sMode=MODE_NONE;

SoftwareSerial sBluetooth(TX, RX);

const char *LOG="log:";

//forward
void readBTCmd();
bool processBTCmd(char *, int);
void updateBMP085();
void readMode(const char *);
void changeMode(int aPrevMode, int aCurMode);
void ackMode();
void restartParsee();
void release_balloon();
void open_parachute();

void setup() {
  LOG_SERIAL(Serial.begin(9600));
  Wire.begin();
  delay(1000);

	//init
  sDps.init();
  sBluetooth.begin(115200);
	sLed.setMode(LedOut::EMiddle);

  adxl.powerOn();
	//set values for what is considered freefall (0-255)
	adxl.setFreeFallThreshold(2); //7 - (5 - 9) recommended - 62.5mg per increment
	adxl.setFreeFallDuration(1); //45 - (20 - 70) recommended - 5ms per increment
  adxl.setInterruptMapping(ADXL345_INT_FREE_FALL_BIT, ADXL345_INT1_PIN);
  adxl.setInterrupt(ADXL345_INT_FREE_FALL_BIT,  1);

	digitalWrite(STBY, LOW);

	restartParser();
}

void loop() {
  unsigned long msec=millis();

	updateBMP085(msec);
	updateADXL345();
	readBTCmd();
  sLed.update(msec);

  delay(200);
}

long sParachuteReleaseTime=-1;
int sParachuteNumAttempts=5;

void updateADXL345() {
	byte interrupts = adxl.getInterruptSource();
 
	//freefall
	if(adxl.triggered(interrupts, ADXL345_FREE_FALL)){
		//add code here to do when freefall is sensed
		if(!sSecurityMode) {
			sFreefallDetected=true;
			sBluetooth.print(LOG);
			sBluetooth.println("FREEF");
		}
		else {
			sBluetooth.print(LOG);
			sBluetooth.println("freef ins sec");
		}
	}
}

void updateBMP085(long time) {
  sDps.getPressure(&sPressure);
  sDps.getTemperature(&sTemperature);

	if(sFirstPressure) {
		sFirstPressure=false;
		sStartPressure = sPressure;
	}

	//we have to reach a minimal height -> unlock security setting for parachute
	if(sSecurityMode) {
		if(sStartPressure - sPressure > 20000) {
			sSecurityMode=false;
		}
	}
	if(!sSecurityMode) {
		//in exceptional situations, possibly without communication to the host computer we open the parachute autonomously
		if(sStartPressure - sPressure < 10000 && (sParachuteReleaseTime=-1 || time-sParachuteReleaseTime>5000) && sParachuteNumAttempts>0) {
			//we are approaching the ground
			//open parachute
			open_parachute();
			sLed.setMode(LedOut::ESlow);
			sParachuteReleaseTime=time;
			sParachuteNumAttempts--;

			sBluetooth.print(LOG);
			sBluetooth.print("EMER EX #");
			sBluetooth.println(sParachuteNumAttempts);
		}
	}
}

void readBTCmd() {
	int i;
  for(i=0; i<7 && sBluetooth.available(); ++i) {
    char c = (char)sBluetooth.read();
		if(c=='\n') {
			sCmd[sCmdPos]=0;
			if(sCmdPos) processBTCmd(sCmd, sCmdPos);
			restartParser();
		}
		else {
			sCmd[sCmdPos++]=c;
			if(sCmdPos==CMDSIZE) {
				sCmd[sCmdPos]=0;
				processBTCmd(sCmd, sCmdPos);
				restartParser();
			}
		}
  }
}

void restartParser() {
	for(int i=0; i<CMDSIZE; ++i) {
		sCmd[i]=0;
	}
	sCmdPos=0;
}

bool processBTCmd(char *cmd, int len) {
	int _xor=0;
	int nxor=0;
	if(len>2) {
		int i;
		_xor=cmd[len-1];
		for(i=0; i<len-1; ++i) {
			nxor^=cmd[i];
		}
		if(nxor==_xor) {
			cmd[len-1]=0;
			len--; //xor replaced by '0'
			if(len > 2 && cmd[0]=='m' && cmd[1]=='o') {
				//read and process mode
				readMode(cmd+2);
				ackMode();
			}
			return true;
		}
		LOG_SERIAL(Serial.print(_xor));
		LOG_SERIAL(Serial.print("<->"));
		LOG_SERIAL(Serial.println(nxor));

		return false;
	}

	return false;
}

void readMode(const char *aStr) {
	if(aStr[0] >= '0' && aStr[0] <= '9') {
		int mode=aStr[0]-'0';
		if(mode >=0 && mode <=MAX_MODE) {
			//we have a valid mode
			if(mode != sMode) {
				//we have a new valid mode
				sPrevMode = sMode;
				sMode=mode;
				changeMode(sPrevMode, sMode);
			}
		}
	}
}

void changeMode(int aPrevMode, int aCurMode) {
	if(aCurMode == MODE_CLIMBING) {
		sLed.setMode(LedOut::EMiddle);
	}
	else if(aCurMode == MODE_FREE_FALL ||
					aCurMode == MODE_TEST_BALLOON) {
		// release balloon
		release_balloon();
		sLed.setMode(LedOut::EFast);
	}
	else if(aCurMode == MODE_PARACHUTE_1 ||
					aCurMode == MODE_PARACHUTE_2 ||
					aCurMode == MODE_PARACHUTE_3 ||
					aCurMode == MODE_TEST_PARACHUTE) {
		//open parachute
		open_parachute();
		//release balloon if not yet done
		release_balloon();
		sLed.setMode(LedOut::ESlow);
	}
	else {
		sLed.setMode(LedOut::ESlower);
	}	

	sBluetooth.print(LOG);
	sBluetooth.print('m');
	sBluetooth.println(aCurMode);
}

void release_balloon() {
	digitalWrite(STBY, HIGH);
	digitalWrite(BIN1, HIGH);
	analogWrite(PWMB, 255);
	delay(200);
	digitalWrite(BIN1, LOW);
	analogWrite(PWMB, 0);
	digitalWrite(STBY, LOW);
}

void open_parachute() {
	digitalWrite(STBY, HIGH);
	digitalWrite(AIN1, HIGH);
	analogWrite(PWMA, 255);
	delay(200);
	digitalWrite(AIN1, LOW);
	analogWrite(PWMA, 0);
	digitalWrite(STBY, LOW);
}

void ackMode() {
  const char *ACK="ack:";
	const char SEP=':';
	sBluetooth.print(ACK);
	sBluetooth.print(sMode);
	sBluetooth.print(SEP);
	sBluetooth.print(sTemperature);
	sBluetooth.print(SEP);
	sBluetooth.print(sPressure);
	sBluetooth.print(SEP);
	sBluetooth.println(sFreefallDetected?'1':'0');
}
