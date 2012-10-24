// -*-C++-*-
/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/

#ifndef __LEDOUT__
#define __LEDOUT__

/**
LedOut lets blink a LED with a certain frequence. Frequence is given either by default values ENone, ESlow, EMiddle, EFast (setMode) or by the duration of the blink time in msec.
 */
class LedOut {
public:
  enum EBlinkMode {EOff=0, ESlow=4000, EMiddle=1500, EFast=500, EFaster=200, EOn=-1};
	LedOut(int aPin);
	LedOut(int aPin, int aMode);
  void setMode(int aMode, unsigned long aStartTime=0);
  int getMode() {return iMode;}
  void update(unsigned long aTime);
protected:
  int iPin;
	int iMode; //iMode is identical to the duration of one blink (on-off) cycle in msec, a negative value means LED is off
  bool iToggleState;
	unsigned long iStartTime;
private:
	void init(int aPin, int aMode);
};

#endif
