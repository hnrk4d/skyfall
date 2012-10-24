// -*-C++-*-
/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/
#include <arduino.h>
#include "ledout.h"

LedOut::LedOut(int aPin) {
	init(aPin, EOff);
}

LedOut::LedOut(int aPin, int aMode) {
	init(aPin, aMode);
}

void LedOut::init(int aPin, int aMode) {
  iPin=aPin;
  iMode=aMode;
  iToggleState=(iMode==EOff)?false:true;
	iStartTime=0;
}

void LedOut::setMode(int aMode, unsigned long aStartTime) {
	if(iMode != aMode) {
		iMode=aMode;
		iStartTime=aStartTime;
		pinMode(iPin, OUTPUT);
	}
}

void LedOut::update(unsigned long aTime) {
  if(iMode == EOff) {
    if(iToggleState) {
      digitalWrite(iPin, LOW);
      iToggleState=false;
		}
	}
	else if(iMode == EOn) {
    if(!iToggleState) {
      digitalWrite(iPin, HIGH);
      iToggleState=true;
		}
	}
	else {
    bool oldToggleState=iToggleState;
		int delta=(int)((aTime-iStartTime) % (unsigned long)iMode);
		if (delta > (iMode >> 1)) {
			//LED on
			iToggleState=true;
		}
		else {
			//LED off
			iToggleState=false;
		}
		if(iToggleState != oldToggleState) {
			digitalWrite(iPin, iToggleState);
		}
	}
}
