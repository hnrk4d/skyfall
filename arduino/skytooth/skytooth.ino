// -*-C++-*-
/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/

#include <stdlib.h>
#include <SoftwareSerial.h>
#include <Wire.h>
#include <BMP085.h>
#include <ledout.h>

//BMP085 variables
BMP085 sDps = BMP085();      // Digital Pressure Sensor 
long sTemperature=0, sPressure=0;

//Motor control - TB6612FNG
#define AIN1 4
#define PWMA 5
#define BIN1 7
#define PWMB 6

//Bluetooth module
#define TX 2
#define RX 3

//LED
#define LED 13
LedOut sLed(LED);

//General
#define CMDSIZE 10
char sCmd[CMDSIZE+1];
int sCmdPos=0;

//Modes
#define MODE_NONE 0
#define MODE_CLIMBING 1
#define MODE_FREE_FALL 2
#define MODE_PARACHUTE 3
#define MAX_MODE 3

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

void setup() {
  //Setup usb serial connection to computer
  Serial.begin(9600);
  Wire.begin();
  delay(1000);

	//init
  sDps.init();
  sBluetooth.begin(115200);
	sLed.setMode(LedOut::EMiddle);
}

void loop() {
  unsigned long msec=millis();

	updateBMP085();
	readBTCmd();
  sLed.update(msec);

  delay(50);
}

void updateBMP085() {
  sDps.getPressure(&sPressure);
  sDps.getTemperature(&sTemperature);
}

void readBTCmd() {
	int i;
  //Read from sBluetooth and write to usb serial
  for(i=0; i<7 && sBluetooth.available(); ++i) {
    char c = (char)sBluetooth.read();
		if(c=='\n') {
			sCmd[sCmdPos]=0;
			if(sCmdPos) processBTCmd(sCmd, sCmdPos);
			sCmdPos=0; //restart
		}
		else {
			sCmd[sCmdPos++]=c;
			if(sCmdPos==CMDSIZE) {
				sCmd[sCmdPos]=0;
				processBTCmd(sCmd, sCmdPos);
				sCmdPos=0; //restart
			}
		}
  }
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
			if(len >=3 && cmd[0]=='m' && cmd[1]=='o') {
				//read and process mode
				readMode(cmd+2);
				ackMode();
			}
			else {
				//Serial.println(cmd);
				//Serial.print("??");
			}
			return true;
		}
		Serial.print(_xor);
		Serial.print("<->");
		Serial.println(nxor);

		/*
		sBluetooth.print(LOG);
		sBluetooth.print(_xor);
		sBluetooth.print(':');
		sBluetooth.println(nxor);
		*/
		return false;
	}

	/*
	sBluetooth.print(LOG);
	sBluetooth.println("empty msg");
	*/

	return false;
}

void readMode(const char *aStr) {
	int mode=atoi(aStr);
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

void changeMode(int aPrevMode, int aCurMode) {
	if(aCurMode == MODE_FREE_FALL) {
		// release balloon
		digitalWrite(AIN1, HIGH);
		analogWrite(PWMA, 255);
		sLed.setMode(LedOut::EFast);
	}
	else if(aCurMode == MODE_PARACHUTE) {
		//open parachute
		digitalWrite(BIN1, HIGH);
		analogWrite(PWMB, 255);
		sLed.setMode(LedOut::EFaster);
	}
	else if(aCurMode == MODE_NONE) {
		sLed.setMode(LedOut::ESlow);
	}	

	sBluetooth.print(LOG);
	sBluetooth.print('m');
	sBluetooth.println(aCurMode);
}

void ackMode() {
  const char *ACK="ack:";
	const char SEP=':';
	sBluetooth.print(ACK);
	sBluetooth.print(sMode);
	sBluetooth.print(SEP);
	sBluetooth.print(sTemperature);
	sBluetooth.print(SEP);
	sBluetooth.println(sPressure);
}
