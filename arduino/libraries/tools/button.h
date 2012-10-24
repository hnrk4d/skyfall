// -*-C++-*-
/*

Copyright (c) 2012, Henrik Battke. All rights reserved.
Author(s): Henrik Battke

*/

#ifndef __BUTTON__
#define __BUTTON__

/**
Button reads a digital input.
 */
class Button {
public:
	enum {EButtonPressed, EButtonReleased};

  void init(int aPin);
	void update(unsigned long aTime);
  int getMode() {return iMode;}
  virtual void buttonUp() {}
  virtual void buttonDown() {}

protected:
	int iMode;
  int iPin;
};

#endif
