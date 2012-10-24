#include <SoftwareSerial.h>

#define TX 2
#define RX 3

#define CMDSIZE 10
char sCmd[CMDSIZE+1];
int sCmdPos=0;

SoftwareSerial bluetooth(TX, RX);

//forward
void readCmd();
bool processCmd(char *, int);

void setup() {
  //Setup usb serial connection to computer
  Serial.begin(9600);
  
  //Setup Bluetooth serial connection to android
  bluetooth.begin(115200);
  //bluetooth.print("AT+NAMEskyterm\r\n");
  //delay(1000);
  //bluetooth.print("$$$");
  //delay(100);
  //bluetooth.println("U,9600,N");
  //bluetooth.begin(9600);
}

void loop() {
	readCmd();
  
  //Read from usb serial to bluetooth
  if(Serial.available()) {
    char toSend = (char)Serial.read();
    bluetooth.println(toSend);
  }
  delay(100);
}

void readCmd() {
	int i;
  //Read from bluetooth and write to usb serial
  for(i=0; i<7 && bluetooth.available(); ++i) {
    char c = (char)bluetooth.read();
		//Serial.print(c);
		if(c=='\n') {
			sCmd[sCmdPos]=0;
			if(sCmdPos) processCmd(sCmd, sCmdPos);
			sCmdPos=0; //restart
		}
		else {
			sCmd[sCmdPos++]=c;
			if(sCmdPos==CMDSIZE) {
				sCmd[sCmdPos]=0;
				processCmd(sCmd, sCmdPos);
				sCmdPos=0; //restart
			}
		}
  }
}

bool processCmd(char *cmd, int len) {
  const char *ACK="ack";
  const char *LOG="log:";
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
			String str(cmd);
			if(str.compareTo("ping")==0) {
				Serial.println(cmd);
				bluetooth.println(ACK);
			}
			else {
				Serial.print("unrec.:");
				Serial.println(cmd);
			}
			return true;
		}
		Serial.print("read error ");
		Serial.print(_xor);
		Serial.print(" <-> ");
		Serial.println(nxor);
		bluetooth.print(LOG);
		bluetooth.print(_xor);
		bluetooth.print(':');
		bluetooth.println(nxor);
		return false;
	}

	Serial.println("empty msg");
	bluetooth.print(LOG);
	bluetooth.println("empty msg");
	return false;
}
