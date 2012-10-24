// -*-C++-*-
/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/
#include <arduino.h>
#include "button.h"

void Button::init(int aPin) {
	iPin=aPin;
  iMode=EButtonReleased;
	pinMode(iPin, INPUT);
}

void Button::update(unsigned long aTime) {
	int state=digitalRead(iPin);
	switch(iMode) {
	case EButtonReleased: {
		if(state == HIGH) {
			iMode=EButtonPressed;
			buttonDown();
		}
	} break;
	case EButtonPressed: {
		if(state == LOW) {
			iMode=EButtonReleased;
			buttonUp();
		}
	} break;
	}
}
